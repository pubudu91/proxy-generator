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

package dev.choreo.apim.utils;

public class Names {
    public static final String INCOMING_REQUEST = "incomingRequest";
    public static final String CALLER = "caller";
    public static final String BACKEND_ENDPOINT = "backendEP";
    public static final String BACKEND_RESPONSE = "backendResponse";
    public static final String ERROR_FLOW_RESPONSE = "errFlowResponse";
    public static final String UPDATED_HEADERS = "updatedHeaders";
    public static final String ERROR = "e";
    public static final String BUILTIN_POLICY_ORG = System.getProperty("policy.org");
    public static final String POLICY_VALIDATOR_PKG = "policy_validator";
    public static final String POLICY_IN_FLOW_ANNOT = "InFlow";
    public static final String POLICY_OUT_FLOW_ANNOT = "OutFlow";
    public static final String POLICY_FAULT_FLOW_ANNOT = "FaultFlow";
    public static final String MEDIATION_CONTEXT_TYPE = "MediationContext";
    public static final String MEDIATION_CONTEXT_VAR = "mediationCtx";
    public static final String MEDIATION_CONTEXT_HTTP_METHOD = MEDIATION_CONTEXT_VAR + ".httpMethod";
    public static final String MEDIATION_CONTEXT_RESOURCE_PATH = MEDIATION_CONTEXT_VAR + ".resourcePath";
}
