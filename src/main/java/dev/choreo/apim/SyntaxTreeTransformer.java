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
import dev.choreo.apim.code.builders.DoBlock;
import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.Document;
import io.ballerina.tools.text.LineRange;
import io.ballerina.tools.text.TextDocumentChange;
import io.ballerina.tools.text.TextEdit;
import io.ballerina.tools.text.TextRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.choreo.apim.utils.Names.BACKEND_ENDPOINT;
import static dev.choreo.apim.utils.Names.BACKEND_RESPONSE;
import static dev.choreo.apim.utils.Names.CALLER;
import static dev.choreo.apim.utils.Names.ERROR_FLOW_RESPONSE;
import static dev.choreo.apim.utils.Names.INCOMING_REQUEST;
import static dev.choreo.apim.utils.Names.UPDATED_HEADERS;
import static java.lang.String.format;

public class SyntaxTreeTransformer extends NodeVisitor {

    private static final TextRange START_POS = TextRange.from(0, 0);
    private final String inflowTemplate;
    private final String outflowTemplate;
    private final String faultflowTemplate;
    private final PolicyManager policyManager;
    private CodeGenerator codegen;
    private List<TextEdit> edits;
    private CodeContext ctx;

    public SyntaxTreeTransformer(String inflowTemplate, String outflowTemplate, String faultflowTemplate,
                                 PolicyManager policyManager) {
        this.inflowTemplate = inflowTemplate;
        this.outflowTemplate = outflowTemplate;
        this.faultflowTemplate = faultflowTemplate;
        this.policyManager = policyManager;
    }

    public TextDocumentChange modifyDoc(Document document, Map<String, Operation> operations) {
        this.edits = new ArrayList<>();
        this.codegen = new CodeGenerator(inflowTemplate, outflowTemplate, faultflowTemplate, policyManager, operations);
        visitNode(document.syntaxTree().rootNode());
        this.edits.add(0, TextEdit.from(START_POS, codegen.generateImports()));
        return TextDocumentChange.from(this.edits.toArray(new TextEdit[0]));
    }

    private void visitNode(Node node) {
        CodeContext prevCtx = this.ctx;
        this.ctx = new CodeContext(prevCtx, node);
        node.accept(this);
        this.ctx = prevCtx;
    }

    @Override
    public void visit(ModulePartNode modulePartNode) {
        for (ModuleMemberDeclarationNode member : modulePartNode.members()) {
            if (member.kind() == SyntaxKind.SERVICE_DECLARATION) {
                visitNode(member);
            }
        }
    }

    @Override
    public void visit(ServiceDeclarationNode serviceDeclarationNode) {
        for (Node member : serviceDeclarationNode.members()) {
            if (member.kind() == SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
                visitNode(member);
            }
        }
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        visitNode(functionDefinitionNode.functionSignature());
        visitNode(functionDefinitionNode.functionBody());
    }

    @Override
    public void visit(FunctionSignatureNode functionSignature) {
        TextRange cursorPos = TextRange.from(functionSignature.openParenToken().textRange().endOffset(), 0);
        String paramSignature = format("http:Caller %s, http:Request %s", CALLER, INCOMING_REQUEST);
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
        // TODO: 2022-06-03 Move to code gen
        String code = doBlock
                .addStatement(this.codegen.generateInflow(this.ctx))
                .addStatement(getBackendHTTPCall(this.ctx.resourceMethodName()))
                .addStatement(this.codegen.generateOutflow(this.ctx))
                .addStatement(format("check %s->respond(%s);", CALLER, BACKEND_RESPONSE))
                .addStatementToOnFail(format("http:Response %s = createDefaultErrorResponse();", ERROR_FLOW_RESPONSE))
                .addStatementToOnFail(this.codegen.generateFaultFlow(this.ctx))
                .addStatementToOnFail(format("check %s->respond(%s);", CALLER, ERROR_FLOW_RESPONSE))
                .build();
        TextEdit body = TextEdit.from(start, code);
        edits.add(body);
    }

    private String getBackendHTTPCall(String functionName) {
        if ("get".equalsIgnoreCase(functionName)
                || "head".equalsIgnoreCase(functionName)
                || "options".equalsIgnoreCase(functionName)) {
            StringBuilder builder = new StringBuilder();
            builder.append(format("map<string|string[]> %s = copyRequestHeaders(%s);\n", UPDATED_HEADERS,
                                  INCOMING_REQUEST));
            builder.append(format("http:Response %s = check %s->%s(\"...\", %s);\n", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                                  this.ctx.resourceMethodName(), UPDATED_HEADERS));
            return builder.toString();
        }

        return format("http:Response %s = check %s->%s(\"...\", %s);\n", BACKEND_RESPONSE, BACKEND_ENDPOINT,
                      this.ctx.resourceMethodName(), INCOMING_REQUEST);
    }
}
