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

import dev.choreo.apim.artifact.model.Operation;
import dev.choreo.apim.artifact.model.Policy;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.bala.BalaProject;
import io.ballerina.projects.repos.FileSystemCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public class PolicyPackageLoader {

    private static final Path CENTRAL_CACHE = Paths.get("repositories", "central.ballerina.io", "bala");
    private final Path localRepo;
    private final ProjectEnvironmentBuilder envBuilder;

    public PolicyPackageLoader(Path baldistHome, Path localRepo) {
        this.localRepo = localRepo;
        this.envBuilder = ProjectEnvironmentBuilder.getDefaultBuilder()
                .addCompilationCacheFactory(
                        new FileSystemCache.FileSystemCacheFactory(baldistHome.resolve("repo/cache")));
    }

    public PolicyPackage loadPackage(PackageID id) {
        Path policyPath = this.localRepo.resolve(
                Paths.get("repositories/central.ballerina.io/bala", id.org(), id.name(), id.version(), "any"));
        Project balaProject = BalaProject.loadProject(this.envBuilder, policyPath);
        return new PolicyPackage(balaProject);
    }

    public void pullPolicies(Collection<Operation> ops) {
        for (Operation op : ops) {
            op.getOperationPolicies().getRequest().forEach(this::pullPolicy);
            op.getOperationPolicies().getResponse().forEach(this::pullPolicy);
            op.getOperationPolicies().getFault().forEach(this::pullPolicy);
        }
    }

    private void pullPolicy(Policy policy) {
        String[] nameCmpts = policy.getPolicyName().split("/");

        if (nameCmpts.length != 2) {
            throw new IllegalArgumentException("Invalid policy name format: " + policy.getPolicyName());
        }

        Path policyCachePath = this.localRepo.resolve(
                Paths.get(CENTRAL_CACHE.toString(), nameCmpts[0], nameCmpts[1], policy.getPolicyVersion()));

        if (Files.exists(policyCachePath)) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(System.getenv("SHELL"), "-c",
                        String.format("bal pull %s/%s:%s", nameCmpts[0], nameCmpts[1], policy.getPolicyVersion()));

        try {
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            System.out.println(reader.readLine());
            int exitVal = process.waitFor();

            if (exitVal != 0) {
                throw new RuntimeException("'bal pull' command exited unexpectedly");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
