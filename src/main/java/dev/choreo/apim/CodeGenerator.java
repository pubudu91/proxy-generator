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
import dev.choreo.apim.artifact.model.Operation;
import dev.choreo.apim.artifact.model.Policy;
import dev.choreo.apim.code.builders.DoBlock;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dev.choreo.apim.utils.Names.BACKEND_ENDPOINT;
import static dev.choreo.apim.utils.Names.BACKEND_RESPONSE;
import static dev.choreo.apim.utils.Names.CALLER;
import static dev.choreo.apim.utils.Names.ERROR;
import static dev.choreo.apim.utils.Names.ERROR_FLOW_RESPONSE;
import static dev.choreo.apim.utils.Names.INCOMING_REQUEST;
import static dev.choreo.apim.utils.Names.UPDATED_HEADERS;
import static java.lang.String.format;

public class CodeGenerator {

    private final String inflowTemplate;
    private final String outflowTemplate;
    private final String faultflowTemplate;
    private final Map<String, Operation> operations;
    private final PolicyManager policyManager;
    private final Set<String> imports;
    private final String paramSignature = format("http:Caller %s, http:Request %s", CALLER, INCOMING_REQUEST);

    public CodeGenerator(String inflowTemplate, String outflowTemplate, String faultflowTemplate,
                         PolicyManager policyManager, Map<String, Operation> operations) {
        this.inflowTemplate = inflowTemplate;
        this.outflowTemplate = outflowTemplate;
        this.faultflowTemplate = faultflowTemplate;
        this.operations = operations;
        this.policyManager = policyManager;
        this.imports = new HashSet<>();
    }

    public String modifyResourceParamSignature(FunctionSignatureNode functionSignature) {
        return functionSignature.parameters().isEmpty() ? paramSignature : paramSignature + ", ";
    }

    public String modifyResourceReturnSignature() {
        return "error?";
    }

    public String generateDoBlock(CodeContext ctx, int nTabs) {
        DoBlock doBlock = new DoBlock(nTabs);
        return doBlock
                .addStatement(generateInflow(ctx))
                .addStatement(generateBackendHTTPCall(ctx))
                .addStatement(generateOutflow(ctx))
                .addStatement(format("check %s->respond(%s);", CALLER, BACKEND_RESPONSE))
                .addStatementToOnFail(format("http:Response %s = createDefaultErrorResponse(e);", ERROR_FLOW_RESPONSE))
                .addStatementToOnFail(generateFaultFlow(ctx))
                .addStatementToOnFail(format("check %s->respond(%s);", CALLER, ERROR_FLOW_RESPONSE))
                .build();
    }

    public String generateInflow(CodeContext ctx) {
        Operation operation = this.operations.get(ctx.resourceMethodSignature());

        if (operation == null || operation.getOperationPolicies().getRequest().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        for (Policy policy : operation.getOperationPolicies().getRequest()) {
            PolicyPackage pkg = policyManager.get(policy.getPolicyName(), policy.getPolicyVersion());
            builder.append(generateInFlowPolicyInvocation(pkg)).append('\n');
            addToImports(pkg);
        }

        return builder.toString();
    }

    public String generateOutflow(CodeContext ctx) {
        Operation operation = this.operations.get(ctx.resourceMethodSignature());

        if (operation == null || operation.getOperationPolicies().getResponse().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        for (Policy policy : operation.getOperationPolicies().getResponse()) {
            PolicyPackage pkg = policyManager.get(policy.getPolicyName(), policy.getPolicyVersion());
            builder.append(generateOutFlowPolicyInvocation(pkg)).append('\n');
            addToImports(pkg);
        }

        return builder.toString();
    }

    public String generateFaultFlow(CodeContext ctx) {
        Operation operation = this.operations.get(ctx.resourceMethodSignature());

        if (operation == null || operation.getOperationPolicies().getFault().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        for (Policy policy : operation.getOperationPolicies().getFault()) {
            PolicyPackage pkg = policyManager.get(policy.getPolicyName(), policy.getPolicyVersion());
            builder.append(generateFaultFlowPolicyInvocation(pkg)).append('\n');
            addToImports(pkg);
        }

        return builder.toString();
    }

    public String generateImports() {
        StringBuilder builder = new StringBuilder();

        for (String imprt : this.imports) {
            builder.append(String.format("import %s;\n", imprt));
        }

        return builder.toString();
    }

    public String generateBackendHTTPCall(CodeContext ctx) {
        String functionName = ctx.resourceMethodName();

        if ("get".equalsIgnoreCase(functionName)
                || "head".equalsIgnoreCase(functionName)
                || "options".equalsIgnoreCase(functionName)) {
            return format("map<string|string[]> %s = copyRequestHeaders(%s);\n", UPDATED_HEADERS,
                          INCOMING_REQUEST) +
                    format("http:Response %s = check %s->%s(\"...\", %s);\n", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                           ctx.resourceMethodName(), UPDATED_HEADERS);
        }

        return format("http:Response %s = check %s->%s(\"...\", %s);\n", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                      ctx.resourceMethodName(), INCOMING_REQUEST);
    }

    private String generateInFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getInFlowPolicy().isEmpty()) {
            return "";
        }
        JsonObject func = pkg.getInFlowPolicy().get();
        String fnCall = format("%s:%s(%s)", pkg.name(), func.get("name").getAsString(), INCOMING_REQUEST);
        return format(this.inflowTemplate, fnCall);
    }

    private String generateOutFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getOutFlowPolicy().isEmpty()) {
            return "";
        }
        JsonObject func = pkg.getOutFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s)", pkg.name(), func.get("name").getAsString(), BACKEND_RESPONSE,
                               INCOMING_REQUEST);
        return format(this.outflowTemplate, fnCall);
    }

    private String generateFaultFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getFaultFlowPolicy().isEmpty()) {
            return "";
        }
        JsonObject func = pkg.getFaultFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s, %s, %s)", pkg.name(), func.get("name").getAsString(), ERROR_FLOW_RESPONSE,
                               ERROR, BACKEND_RESPONSE, INCOMING_REQUEST);
        return format(this.faultflowTemplate, fnCall);
    }

    private void addToImports(PolicyPackage pkg) {
        String pkgName = pkg.org() + "/" + pkg.name(); // TODO: 2022-06-07 Need to consider quoted identifiers
        this.imports.add(pkgName);
    }
}
