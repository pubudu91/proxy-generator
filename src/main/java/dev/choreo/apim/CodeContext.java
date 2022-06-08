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

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;

import static dev.choreo.apim.utils.Utils.buildOpKey;

public class CodeContext {

    private final CodeContext parent;
    private final Node syntaxTreeNode;
    private String resourceSignature;

    private String resourceMethodName;

    public CodeContext(CodeContext parent, Node syntaxTreeNode) {
        this.parent = parent;
        this.syntaxTreeNode = syntaxTreeNode;
    }

    public CodeContext parent() {
        return parent;
    }

    public Node node() {
        return syntaxTreeNode;
    }

    public String resourceMethodSignature() {
        if (this.resourceSignature != null) {
            return this.resourceSignature;
        }
        FunctionDefinitionNode resource = getResourceMethodNode(this.syntaxTreeNode);
        this.resourceSignature = buildOpKey(resource.functionName().text(),
                                            getResourcePath(resource.relativeResourcePath()));
        return this.resourceSignature;
    }

    public String resourceMethodName() {
        if (this.resourceMethodName != null) {
            return this.resourceMethodName;
        }
        FunctionDefinitionNode resource = getResourceMethodNode(this.syntaxTreeNode);
        this.resourceMethodName = resource.functionName().text();
        return this.resourceMethodName;
    }

    private FunctionDefinitionNode getResourceMethodNode(Node node) {
        while (node != null && node.kind() != SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
            node = node.parent();
        }

        if (node == null) {
            throw new IllegalStateException("Current code context is not within a resource method");
        }

        return (FunctionDefinitionNode) node;
    }

    private String getResourcePath(NodeList<Node> pathSegments) {
        StringBuilder pathBuilder = new StringBuilder("/");

        for (Node pathSegment : pathSegments) {
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
