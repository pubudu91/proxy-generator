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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.choreo.apim.utils.ProjectAPIUtils;
import io.ballerina.compiler.api.symbols.FunctionSymbol;
import io.ballerina.projects.Module;
import io.ballerina.projects.PackageDescriptor;
import io.ballerina.projects.Project;
import io.ballerina.projects.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class PolicyPackage {

    private String org;
    private String name;
    private String version;
    private Project project;
    private JsonObject inflowPolicy;
    private JsonObject outflowPolicy;
    private JsonObject faultflowPolicy;
    private List<FunctionSymbol> publicFns;

    public PolicyPackage(Project project) {
        this.project = project;
        PackageDescriptor descriptor = project.currentPackage().descriptor();
        this.org = descriptor.org().value();
        this.name = descriptor.name().value();
        this.version = descriptor.version().toString();

        Module module = this.project.currentPackage().getDefaultModule();
        Resource document = ProjectAPIUtils.getResource(module, "policy-meta.json");
        String metajson = new String(document.content(), StandardCharsets.UTF_8);
        JsonObject policyMeta = JsonParser.parseString(metajson).getAsJsonObject();
        this.inflowPolicy = policyMeta.getAsJsonObject("inflow");
        this.outflowPolicy = policyMeta.getAsJsonObject("outflow");
        this.faultflowPolicy = policyMeta.getAsJsonObject("faultflow");
    }

    public Optional<JsonObject> getInFlowPolicy() {
        return Optional.ofNullable(this.inflowPolicy);
    }

    public Optional<JsonObject> getOutFlowPolicy() {
        return Optional.ofNullable(this.outflowPolicy);
    }

    public Optional<JsonObject> getFaultFlowPolicy() {
        return Optional.ofNullable(this.faultflowPolicy);
    }
}
