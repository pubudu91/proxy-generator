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

import static dev.choreo.apim.utils.Names.BACKEND_RESPONSE;
import static dev.choreo.apim.utils.Names.ERROR;
import static dev.choreo.apim.utils.Names.ERROR_FLOW_RESPONSE;
import static dev.choreo.apim.utils.Names.INCOMING_REQUEST;
import static java.lang.String.format;

public class CodeGenerator {

    private final String inflowTemplate;
    private final String outflowTemplate;
    private final String faultflowTemplate;

    public CodeGenerator(String inflowTemplate, String outflowTemplate, String faultflowTemplate) {
        this.inflowTemplate = inflowTemplate;
        this.outflowTemplate = outflowTemplate;
        this.faultflowTemplate = faultflowTemplate;
    }

    public String generateInFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getInFlowPolicy().isEmpty()) {
            return "";
        }

        JsonObject func = pkg.getInFlowPolicy().get();
        String fnCall = format("%s:%s(%s)", pkg.name(), func.get("name").getAsString(), INCOMING_REQUEST);
        return format(this.inflowTemplate, fnCall);
    }

    public String generateOutFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getOutFlowPolicy().isEmpty()) {
            return "";
        }

        JsonObject func = pkg.getOutFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s)", pkg.name(), func.get("name").getAsString(), BACKEND_RESPONSE,
                               INCOMING_REQUEST);
        return format(this.outflowTemplate, fnCall);
    }

    public String generateFaultFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getFaultFlowPolicy().isEmpty()) {
            return "";
        }

        JsonObject func = pkg.getFaultFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s, %s, %s)", pkg.name(), func.get("name").getAsString(), ERROR_FLOW_RESPONSE,
                               ERROR, BACKEND_RESPONSE, INCOMING_REQUEST);
        return format(this.faultflowTemplate, fnCall);
    }
}
