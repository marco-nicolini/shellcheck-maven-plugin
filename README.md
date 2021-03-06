# shellcheck-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/dev.dimlight/shellcheck-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/dev.dimlight/shellcheck-maven-plugin)

A maven plugin to execute shellcheck in a maven build

## How it works

The plugin has a single `check` goal that searches for shell files in standard configurable locations and \
invokes shellcheck on them.

Since shellcheck is a non-java application the plugin provides automatic ways to get hold of the shellcheck binary. This
is controlled by the `binaryResolutionMethod` plugin configuration property:

* `embedded` the plugin will use a shellcheck binary embedded in the plugin jar.
    * useful if you're behind proxy and you want zero-hassles in configuring things
    * you're bound to the embedded shellcheck version (currently 0.7.2)
* `download` the binary will be downloaded at plugin execution time.
    * lets you target a specific shellcheck version different from the embedded one
    * the download is performed under the hood by the maven-download-plugin which provides caching, so you won't be
      downloading the same binary over and over
* `external` the path to a shellcheck binary needs to be provided.
    * you have all control
    * requiring external tools to be installed makes the build less self-contained

For "embedded" and "download" resolutions, at plugin execution time, the resolved binary is copied
to `${project.buid.directory}/shellcheck-plugin/shellcheck` and then invoked.

Optionally the plugin can be configured to fail the build if warnings are found (i.e. on non-zero shellcheck exit code)
with the `failBuildIfWarnings` property.

## Usage

The plugin is released on maven central, so you can use it in your build like this (just replace
${shellcheck-maven-plugin.version} with the latest version).

```xml

<build>
    <plugins>
        <plugin>
            <groupId>dev.dimlight</groupId>
            <artifactId>shellcheck-maven-plugin</artifactId>
            <version>{shellcheck-maven-plugin.version}</version>
            <executions>
                <execution>
                    <id>simple-check</id>
                    <phase>verify</phase>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <!-- The source dirs where files to check are searched.
                             This is a FileSet https://maven.apache.org/shared/file-management/fileset.html
                             however the only things making sense to be specified here are:
                             directory, includes and excludes.
                             Includes and Excludes can use ant-style patterns.
                             This example shows the default configuration -->
                        <sourceDirs>
                            <sourceDir>
                                <directory>${project.basedir}/src/main/sh</directory>
                                <includes>
                                    <include>**/*.sh</include>
                                </includes>
                            </sourceDir>
                        </sourceDirs>

                        <!-- the cmdline args to pass to shellcheck 
                             this example maps to the cmdline "shellcheck -a -s bash --format=tty --norc" -->
                        <args>
                            <arg>-a</arg>
                            <arg>-s</arg>
                            <arg>bash</arg>
                            <arg>--format=tty</arg>
                            <arg>--norc</arg>
                        </args>

                        <!-- set to true if you want the build to fail when you have warnings -->
                        <failBuildIfWarnings>false</failBuildIfWarnings>

                        <!-- chose the binary resolution method "embedded", "download" or "external" -->
                        <binaryResolutionMethod>download</binaryResolutionMethod>

                        <!-- if you have chosen "download" as resolution method, you may also provide the url of the shellcheck
                              release archive (zip or tar.xz) (for all os/arch you're building on) to be used at plugin execution time.
                              The urls are specified as a configuration map, where the exact key for an architecture 
                              (e.g. "Mac_OS_X-x86_64" in the example below) must match what your jvm returns for the 
                              following expression:
                              (System.getProperty("os.name") + "-" + System.getProperty("os.arch")).replace(" ", "_")
                              For your convenience this value is also printed by the plugin almost at start:
                              e.g. "[INFO] os arch: [Mac_OS_X-x86_64]"
                              If you don't provide this configuration map at all (or if you don't provide an exact match
                              to osname-arch as described above) the same url that was used to 
                              fetch the embedded binaries will be used instead. However, at that point
                              you may as well use the embedded binaries -->
                        <releaseArchiveUrls>
                            <Linux-amd64>
                                https://github.com/koalaman/shellcheck/releases/download/v0.7.2/shellcheck-v0.7.2.linux.x86_64.tar.xz
                            </Linux-amd64>
                            <Mac_OS_X-x86_64>
                                https://github.com/koalaman/shellcheck/releases/download/v0.7.2/shellcheck-v0.7.2.darwin.x86_64.tar.xz
                            </Mac_OS_X-x86_64>
                        </releaseArchiveUrls>

                        <!-- If you chose "external" as resolution method you need also to provide the "externalBinaryPath" -->
                        <!-- externalBinaryPath>/path/to/shellcheck</externalBinaryPath -->
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## How to build

### Requirements

* jdk >= 8
* maven >= 3.5.4
* working internet connection needed to retrieve the shellcheck binaries (configure your proxy in your maven
  settings.xml if you're behind one)

```
mvn clean install
```

## Copyright notice

shellcheck-maven-plugin is licensed under the GNU General Public License, v3. A copy of this license is included in the
file LICENSE.txt.

Copyright 2021, Marco Nicolini.

## Shellcheck copyright notice

ShellCheck is licensed under the GNU General Public License.

Copyright 2012-2021, Vidar 'koala_man' Holen and contributors.