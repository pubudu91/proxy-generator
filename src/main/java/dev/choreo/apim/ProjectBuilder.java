/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.choreo.apim;

import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.Project;
import io.ballerina.projects.directory.ProjectLoader;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

class ProjectBuilder {

    private Path projectPath;
    private Path openapiFilePath;

    ProjectBuilder initProject(Path destPath) throws IOException {
        if (destPath == null) {
            throw new IllegalArgumentException("Destination path for the project cannot be 'null'");
        }

        String name = String.format("bal_proxy_%s", Instant.now().toEpochMilli());
        this.projectPath = Files.createDirectory(destPath.resolve(name));
        Files.createFile(this.projectPath.resolve("Ballerina.toml"));
        return this;
    }

    ProjectBuilder addOpenAPIDefinition(InputStream openapiStream) throws IOException {
        if (this.projectPath == null) {
            throw new IllegalStateException("A project needs to be initialized before adding the OpenAPI file");
        }

        this.openapiFilePath = Files.createFile(this.projectPath.resolve("proxy.yaml"));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openapiStream));
             FileWriter writer = new FileWriter(openapiFilePath.toString())) {
            String content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            writer.write(content);
            writer.flush();
        }

        return this;
    }

    Project build() {
        if (this.projectPath == null || this.openapiFilePath == null) {
            throw new IllegalStateException(
                    "A project needs to be initialized and an OpenAPI file added before building the project");
        }

        generateService(this.openapiFilePath, this.projectPath);
        BuildOptions defaultOptions = BuildOptions.builder().setOffline(true).setDumpBirFile(true).build();
        return ProjectLoader.loadProject(this.projectPath, defaultOptions);
    }

    Path getProjectPath() {
        return this.projectPath;
    }

    private void generateService(Path openAPIDef, Path projectPath) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("bal", "openapi", "-i", openAPIDef.toString(), "-o", projectPath.toString(), "--mode",
                        "service");

        try {
            Process process = builder.start();
            InputStream inputStream = process.getErrorStream();
            inputStream.transferTo(System.err);
            int exitVal = process.waitFor();

            if (exitVal != 0) {
                throw new RuntimeException("'bal openapi' command exited unexpectedly");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
