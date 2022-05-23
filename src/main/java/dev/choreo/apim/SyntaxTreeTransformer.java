package dev.choreo.apim;

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
}
