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
import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.projects.Document;
import io.ballerina.tools.text.LineRange;
import io.ballerina.tools.text.TextDocumentChange;
import io.ballerina.tools.text.TextEdit;
import io.ballerina.tools.text.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.choreo.apim.Names.BACKEND_ENDPOINT;
import static dev.choreo.apim.Names.BACKEND_RESPONSE;
import static dev.choreo.apim.Names.CALLER;
import static dev.choreo.apim.Names.ERROR_FLOW_RESPONSE;
import static dev.choreo.apim.Names.INCOMING_REQUEST;
import static dev.choreo.apim.Names.UPDATED_HEADERS;
import static dev.choreo.apim.utils.Utils.buildOpKey;
import static java.lang.String.format;

public class SyntaxTreeTransformer extends NodeVisitor {

    private final String inflowTemplate;
    private final String outflowTemplate;
    private List<TextEdit> edits;
    private List<String> policies;
    private List<String> outflowPolicies;
    private Map<String, Operation> operations;
    private String functionName;
    private String currentOperation;

    public SyntaxTreeTransformer(String inflowTemplate, String outflowTemplate) {
        this.inflowTemplate = inflowTemplate;
        this.outflowTemplate = outflowTemplate;
    }

    public TextDocumentChange modifyDoc(Document document, Map<String, Operation> operations) {
        this.edits = new ArrayList<>();
        this.operations = operations;
        document.syntaxTree().rootNode().accept(this);
        return TextDocumentChange.from(this.edits.toArray(new TextEdit[0]));
    }

    @Override
    public void visit(ModulePartNode modulePartNode) {
        for (ModuleMemberDeclarationNode member : modulePartNode.members()) {
            if (member.kind() == SyntaxKind.SERVICE_DECLARATION) {
                member.accept(this);
            }
        }
    }

    @Override
    public void visit(ServiceDeclarationNode serviceDeclarationNode) {
        for (Node member : serviceDeclarationNode.members()) {
            if (member.kind() == SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
                member.accept(this);
            }
        }
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        this.functionName = functionDefinitionNode.functionName().text();
        this.currentOperation = buildOpKey(this.functionName,
                                           getResourcePath(functionDefinitionNode.relativeResourcePath()));
        functionDefinitionNode.functionSignature().accept(this);
        functionDefinitionNode.functionBody().accept(this);
        this.functionName = null;
        this.currentOperation = null;
    }

    @Override
    public void visit(FunctionSignatureNode functionSignature) {
        TextRange cursorPos = TextRange.from(functionSignature.openParenToken().textRange().endOffset(), 0);
        String paramSignature = format("http:Caller %s, http:Request %s", CALLER, Names.INCOMING_REQUEST);
        String edit = functionSignature.parameters().isEmpty() ? paramSignature : paramSignature + ", ";
        TextEdit params = TextEdit.from(cursorPos, edit);
        edits.add(params);

        if (functionSignature.returnTypeDesc().isEmpty()) {
            return;
        }

        ReturnTypeDescriptorNode returnType = functionSignature.returnTypeDesc().get();
        TextRange returnTypeRange = returnType.type().textRange();
        TextEdit newReturnType = TextEdit.from(returnTypeRange, "error?");
        edits.add(newReturnType);
    }

    @Override
    public void visit(FunctionBodyBlockNode funcBody) {
        LineRange closingBraceLR = funcBody.closeBraceToken().lineRange();
        TextRange closingBraceTR = funcBody.closeBraceToken().textRange();
        TextRange start = TextRange.from(closingBraceTR.startOffset() - closingBraceLR.startLine().offset(), 0);
        DoBlock doBlock = new DoBlock(closingBraceLR.startLine().offset() / 4 + 1);
        String code = doBlock
                .addStatement(generateInflow())
                .addStatement(getBackendHTTPCall(this.functionName))
                .addStatement(generateOutflow())
                .addStatement(format("check %s->respond(%s);", CALLER, BACKEND_RESPONSE))
                .addStatementToOnFail(format("http:Response %s = createDefaultErrorResponse();", ERROR_FLOW_RESPONSE))
                .addStatementToOnFail("// call_error_flow{ };")
                .addStatementToOnFail(format("check %s->respond(%s);", CALLER, ERROR_FLOW_RESPONSE))
                .build();
        TextEdit body = TextEdit.from(start, code);
        edits.add(body);
    }

    private String generateInflow() {
        StringBuilder builder = new StringBuilder();

        Operation operation = this.operations.get(this.currentOperation);

        if (operation == null) {
            return null;
        }

        for (Policy policy : operation.getOperationPolicies().getRequest()) {
            String fnCall = format("%s(%s)", policy.getPolicyName(), INCOMING_REQUEST);
            builder.append(format(this.inflowTemplate, fnCall)).append('\n');
        }

        return builder.toString();
    }

    private String generateOutflow() {
        StringBuilder builder = new StringBuilder();

        Operation operation = this.operations.get(this.currentOperation);

        if (operation == null) {
            return null;
        }

        for (Policy policy : operation.getOperationPolicies().getResponse()) {
            String fnCall = format("%s(%s, %s)", policy.getPolicyName(), BACKEND_RESPONSE, INCOMING_REQUEST);
            builder.append(format(this.outflowTemplate, fnCall)).append('\n');
        }

        return builder.toString();
    }

    private String getBackendHTTPCall(String functionName) {
        if ("get".equalsIgnoreCase(functionName)
                || "head".equalsIgnoreCase(functionName)
                || "options".equalsIgnoreCase(functionName)) {
            StringBuilder builder = new StringBuilder();
            builder.append(format("map<string|string[]> %s = copyRequestHeaders(%s);\n", UPDATED_HEADERS,
                                  INCOMING_REQUEST));
            builder.append(format("http:Response %s = check %s->%s(\"...\", %s);", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                                  this.functionName, UPDATED_HEADERS));
            return builder.toString();
        }

        return format("http:Response %s = check %s->%s(\"...\", %s);", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                      this.functionName, INCOMING_REQUEST);
    }

    private String getResourcePath(NodeList<Node> pathSegments) {
        StringBuilder pathBuilder = new StringBuilder("/");

        for (Node pathSegment : pathSegments) {
            switch (pathSegment.kind()) {
                case IDENTIFIER_TOKEN:
                case SLASH_TOKEN:
                    pathBuilder.append(((Token) pathSegment).text());
                    break;
                case RESOURCE_PATH_SEGMENT_PARAM:
                    pathBuilder.append('*');
                    break;
                case RESOURCE_PATH_REST_PARAM:
                    pathBuilder.append("**");
                    break;
                default:
                    throw new AssertionError("Unexpected syntax kind: " + pathSegment.kind());
            }
        }

        return pathBuilder.toString();
    }
}
