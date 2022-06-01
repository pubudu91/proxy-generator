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

package dev.choreo.apim.code.builders;

import java.util.ArrayList;
import java.util.List;

public class DoBlock {
    private final String indentation;
    private final List<String> doStmts = new ArrayList<>();
    private final List<String> onFailStmts = new ArrayList<>();

    public DoBlock(int nIndents) {
        this.indentation = "\t".repeat(nIndents);
    }

    public DoBlock addStatement(String stmt) {
        if (stmt != null) {
            this.doStmts.add(stmt);
        }
        return this;
    }

    public DoBlock addStatementToOnFail(String stmt) {
        if (stmt != null) {
            this.onFailStmts.add(stmt);
        }
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.indentation).append("do {\n");
        appendStmts(this.doStmts, builder);

        if (this.onFailStmts.isEmpty()) {
            return builder.append(this.indentation).append('}').append('\n').toString();
        }

        builder.append(this.indentation).append("} on fail var e {").append('\n');
        appendStmts(this.onFailStmts, builder);
        builder.append(this.indentation).append('}').append('\n');
        return builder.toString();
    }

    private void appendStmts(List<String> stmts, StringBuilder builder) {
        for (String stmt : stmts) {
            builder.append(this.indentation).append('\t').append(stmt).append('\n');
        }
    }
}
