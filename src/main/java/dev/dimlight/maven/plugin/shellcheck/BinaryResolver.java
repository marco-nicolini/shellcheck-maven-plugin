package dev.dimlight.maven.plugin.shellcheck;

/*-
 * #%L
 * shellcheck-maven-plugin
 * %%
 * Copyright (C) 2020 Marco Nicolini
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Groups differents ways of getting hold of the correct shellcheck binary.
 */
public class BinaryResolver {

    private final Path mavenTargetDirectory;
    private final Log log;
    private final MavenProject mavenProject;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final Optional<Path> externalBinaryPath;
    private final Architecture arch;
    private final PluginPaths pluginPaths;

    /**
     * @param mavenProject         maven component for the delegated plugin download
     * @param mavenSession         maven component for the delegated plugin download
     * @param pluginManager        maven component for the delegated plugin download
     * @param mavenTargetDirectory the current projec toutput directory
     * @param log                  a maven logger
     */
    public BinaryResolver(MavenProject mavenProject, MavenSession mavenSession, BuildPluginManager pluginManager,
                          Path mavenTargetDirectory,
                          Optional<Path> externalBinaryPath, Log log) {
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.mavenTargetDirectory = mavenTargetDirectory;
        this.externalBinaryPath = externalBinaryPath;
        this.log = log;
        this.arch = Architecture.detect();
        this.pluginPaths = new PluginPaths(mavenTargetDirectory);
    }

    /**
     * Performs binary resolution.
     *
     * @param resolutionMethod the desiderd resolution method.
     * @return a executable shellcheck binary path
     * @throws MojoExecutionException if there are problems while resolving
     * @throws IOException            in case some io operation fails (e.g download or permission change)
     */
    public Path resolve(BinaryResolutionMethod resolutionMethod) throws MojoExecutionException, IOException {
        switch (resolutionMethod) {
            case external:
                return validateExternalBinary();
            case download:
                return downloadShellcheckBinary();
            case embedded:
                return extractEmbeddedShellcheckBinary();
            default:
                throw new IllegalStateException("Invalid resolution method: " + resolutionMethod);
        }
    }

    private Path validateExternalBinary() throws MojoExecutionException {
        return externalBinaryPath
                .map(Path::toFile)
                .filter(File::exists)
                .filter(File::canRead)
                .filter(file -> !arch.isUnixLike() || file.canExecute())
                .map(File::toPath)
                .orElseThrow(() -> new MojoExecutionException("The external shellcheck binary has not been provided or cannot be found or is not readable/ executable"));
    }

    /**
     * Downloads shellcheck for the current architecture and returns the path of the downloaded binary.
     * <p>
     * The actual download is delegated to a maven plugin executed via mojo-executor.
     * This is less clean than a proper implementation but it's also orders of magnitude simpler as it automatically
     * deals with: caching, different compression formats and maven proxy settings.
     *
     * @return the path to the downloaded binary
     * @throws MojoExecutionException if the delegated execution to the download maven plugin fails or if we can't find
     *                                the file after the download
     * @throws IOException            in case we fail to make the downloaded binary executable (unix only)
     */
    private Path downloadShellcheckBinary() throws MojoExecutionException, IOException {
        final Architecture arch = Architecture.detect();
        executeMojo(
                plugin(
                        groupId("com.googlecode.maven-download-plugin"),
                        artifactId("download-maven-plugin"),
                        version("1.6.0")
                ),
                goal("wget"),
                configuration(
                        element(name("uri"), arch.downloadUrl()), // url is an alias!
                        element(name("unpack"), "true"),
                        element(name("outputDirectory"), pluginPaths.getPluginOutputDirectory().toFile().getAbsolutePath())
                ),
                executionEnvironment(
                        mavenProject,
                        mavenSession,
                        pluginManager
                )
        );

        final Path expectedDownloadedBinary = pluginPaths.downloadedAndUnpackedBinPath(arch);
        if (!expectedDownloadedBinary.toFile().exists()) {
            throw new MojoExecutionException("Could not find extracted file [" + expectedDownloadedBinary + "]");
        }

        arch.makeExecutable(expectedDownloadedBinary);

        return expectedDownloadedBinary;
    }

    /**
     * Extracts the shellcheck binary choosing from the binaries embedded in the jar according to the detected arch.
     *
     * @return the path to the usable, architecture-dependent, shellcheck binary.
     * @throws IOException            if something goes bad while extracting and copying to the project build directory.
     * @throws MojoExecutionException if the extracted file cannot be read or executed.
     */
    private Path extractEmbeddedShellcheckBinary() throws IOException, MojoExecutionException {
        log.debug("Detected arch is [" + arch + "]");

        final String binaryTargetName = "shellcheck" + arch.executableSuffix();
        final Path binaryPath = pluginPaths.getPathInPluginOutputDirectory(binaryTargetName);

        final boolean created = binaryPath.toFile().mkdirs();
        log.debug("Path [" + binaryPath + "] was created? [" + created + "]");

        // copy from inside the jar to /target/shellcheck
        final String binResourcePath = arch.embeddedBinPath();
        log.debug("Will try to use binary [" + binResourcePath + "]");
        try (final InputStream resourceAsStream = getClass().getResourceAsStream(binResourcePath)) {
            if (resourceAsStream == null) {
                throw new MojoExecutionException("No embedded binary found for shellcheck");
            }
            Files.copy(resourceAsStream, binaryPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // make the extracted file executable
        arch.makeExecutable(binaryPath);

        final File binaryFile = binaryPath.toFile();
        // check that we copied it and we can execute it.
        if (!binaryFile.exists()) {
            throw new MojoExecutionException("Could not find extracted file [" + binaryFile + "]");
        }

        if (!binaryFile.canExecute()) {
            throw new MojoExecutionException("Extracted file [" + binaryFile + "] is not executable");
        }

        return binaryPath;
    }
}