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
import dev.choreo.apim.code.builders.DoBlock;
import dev.choreo.apim.code.builders.MappingConstructorBuilder;
import io.ballerina.compiler.api.symbols.FunctionSymbol;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.ResourcePathParameterNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dev.choreo.apim.utils.Names.BACKEND_ENDPOINT;
import static dev.choreo.apim.utils.Names.BACKEND_RESPONSE;
import static dev.choreo.apim.utils.Names.BUILTIN_POLICY_ORG;
import static dev.choreo.apim.utils.Names.CALLER;
import static dev.choreo.apim.utils.Names.ERROR;
import static dev.choreo.apim.utils.Names.ERROR_FLOW_RESPONSE;
import static dev.choreo.apim.utils.Names.INCOMING_REQUEST;
import static dev.choreo.apim.utils.Names.MEDIATION_CONTEXT_HTTP_METHOD;
import static dev.choreo.apim.utils.Names.MEDIATION_CONTEXT_RESOURCE_PATH;
import static dev.choreo.apim.utils.Names.MEDIATION_CONTEXT_TYPE;
import static dev.choreo.apim.utils.Names.MEDIATION_CONTEXT_VAR;
import static dev.choreo.apim.utils.Names.POLICY_VALIDATOR_PKG;
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

    public String modifyListener() {
        return "9090";
    }

    public String modifyResourceParamSignature(FunctionSignatureNode functionSignature) {
        return functionSignature.parameters().isEmpty() ? paramSignature : paramSignature + ", ";
    }

    public String modifyResourceReturnSignature() {
        return "error?";
    }

    public String generateMediationContextRecord(CodeContext ctx) {
        // TODO: 2022-06-10 See if it'd be better to use the Semantic API to get the field info of MediationContext.
        MappingConstructorBuilder builder = new MappingConstructorBuilder();
        Node node = ctx.node();

        while (node != null && node.kind() != SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
            node = node.parent();
        }

        if (node == null) {
            throw new AssertionError("Failed to find a resource method context");
        }

        FunctionDefinitionNode func = (FunctionDefinitionNode) node;
        builder.addMapping("httpMethod", String.format("\"%s\"", ctx.resourceMethodName()));

        StringBuilder pathBuilder = new StringBuilder("string `/");
        for (Node pathSegment : func.relativeResourcePath()) {
            switch (pathSegment.kind()) {
                case IDENTIFIER_TOKEN:
                    String path = ((Token) pathSegment).text();
                    if (path.startsWith("'")) {
                        path = path.substring(1);
                    }
                    pathBuilder.append(path);
                    break;
                case SLASH_TOKEN:
                    pathBuilder.append("/");
                    break;
                case RESOURCE_PATH_SEGMENT_PARAM:
                    pathBuilder.append("${").append(((ResourcePathParameterNode) pathSegment).paramName()).append("}");
                    break;
                case RESOURCE_PATH_REST_PARAM:
                    // TODO: 2022-06-10 Ignoring rest params for now. Implement this later.
                    break;
                default:
                    throw new AssertionError("Unexpected syntax kind: " + pathSegment.kind());
            }
        }
        pathBuilder.append("`");
        builder.addMapping("resourcePath", pathBuilder.toString());

        addToImports(BUILTIN_POLICY_ORG, POLICY_VALIDATOR_PKG);
        return String.format("%s:%s %s = %s;\n", POLICY_VALIDATOR_PKG, MEDIATION_CONTEXT_TYPE, MEDIATION_CONTEXT_VAR,
                             builder.build());
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
        return format("http:Response %s = check %s->execute(%s, %s, %s);", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                      MEDIATION_CONTEXT_HTTP_METHOD, MEDIATION_CONTEXT_RESOURCE_PATH, INCOMING_REQUEST);
    }

    private String generateInFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getInFlowPolicy().isEmpty()) {
            return "";
        }
        FunctionSymbol func = pkg.getInFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s)", pkg.name(), func.getName().get(), INCOMING_REQUEST,
                               MEDIATION_CONTEXT_VAR);
        return format(this.inflowTemplate, fnCall);
    }

    private String generateOutFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getOutFlowPolicy().isEmpty()) {
            return "";
        }
        FunctionSymbol func = pkg.getOutFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s, %s)", pkg.name(), func.getName().get(), BACKEND_RESPONSE,
                               INCOMING_REQUEST, MEDIATION_CONTEXT_VAR);
        return format(this.outflowTemplate, fnCall);
    }

    private String generateFaultFlowPolicyInvocation(PolicyPackage pkg) {
        if (pkg.getFaultFlowPolicy().isEmpty()) {
            return "";
        }
        FunctionSymbol func = pkg.getFaultFlowPolicy().get();
        String fnCall = format("%s:%s(%s, %s, %s, %s, %s)", pkg.name(), func.getName().get(), ERROR_FLOW_RESPONSE,
                               ERROR, BACKEND_RESPONSE, INCOMING_REQUEST, MEDIATION_CONTEXT_VAR);
        return format(this.faultflowTemplate, fnCall);
    }

    private void addToImports(PolicyPackage pkg) {
        String pkgName = pkg.org() + "/" + pkg.name(); // TODO: 2022-06-07 Need to consider quoted identifiers
        this.imports.add(pkgName);
    }

    private void addToImports(String org, String name) {
        String pkgName = org + "/" + name;
        this.imports.add(pkgName);
    }
}
