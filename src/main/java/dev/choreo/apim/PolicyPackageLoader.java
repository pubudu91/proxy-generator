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

import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.bala.BalaProject;
import io.ballerina.projects.repos.FileSystemCache;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PolicyPackageLoader {

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
}
