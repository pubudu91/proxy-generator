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
import io.ballerina.tools.text.TextDocumentChange;
import io.ballerina.tools.text.TextEdit;
import io.ballerina.tools.text.TextRange;

import java.util.ArrayList;
import java.util.List;

public class SyntaxTreeTransformer extends NodeVisitor {

    private List<TextEdit> edits;

    public TextDocumentChange modifyDoc(Document document) {
        edits = new ArrayList<>();
        document.syntaxTree().rootNode().accept(this);
        return TextDocumentChange.from(edits.toArray(new TextEdit[0]));
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
        functionDefinitionNode.functionSignature().accept(this);
        functionDefinitionNode.functionBody().accept(this);
    }

    @Override
    public void visit(FunctionSignatureNode functionSignature) {
        TextRange cursorPos = TextRange.from(functionSignature.openParenToken().textRange().endOffset(), 0);
        String edit = functionSignature.parameters().isEmpty() ? "http:Caller caller, http:Request incomingReq" :
                "http:Caller caller, http:Request incomingReq, ";
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
        TextRange start = TextRange.from(funcBody.closeBraceToken().textRange().startOffset(), 0);
        String code = "\tdo {\n" +
                "\t\t\t// call_inflow { }\n" +
                "\t\t\thttp:Response res = check cl->get(\"...\", incomingReq);\n" +
                "\t\t\t// call_outflow { }\n" +
                "\t\t\tcheck caller->respond(res);\n" +
                "\t\t} on fail var e {\n" +
                "\t\t\thttp:Response errorRes = createDefaultErrorResponse();\n" +
                "\t\t\t// call_error_flow{ };\n" +
                "\t\t\tcheck caller->respond(errorRes);\n" +
                "\t\t}\n\t";
        TextEdit body = TextEdit.from(start, code);
        edits.add(body);
    }
}
