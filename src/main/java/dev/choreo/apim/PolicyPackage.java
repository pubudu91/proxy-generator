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

import dev.choreo.apim.utils.Names;
import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.FunctionSymbol;
import io.ballerina.compiler.api.symbols.Qualifier;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.projects.PackageDescriptor;
import io.ballerina.projects.Project;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PolicyPackage {

    private String org;
    private String name;
    private String version;
    private Project project;
    private FunctionSymbol inflowPolicy;
    private FunctionSymbol outflowPolicy;
    private FunctionSymbol faultflowPolicy;
    private List<FunctionSymbol> publicFns;

    public PolicyPackage(Project project) {
        this.project = project;
        PackageDescriptor descriptor = project.currentPackage().descriptor();
        this.org = descriptor.org().value();
        this.name = descriptor.name().value();
        this.version = descriptor.version().toString();
    }

    public Optional<FunctionSymbol> getInFlowPolicy() {
        if (this.inflowPolicy != null) {
            return Optional.of(this.inflowPolicy);
        }

        this.inflowPolicy = getPolicy(Names.POLICY_IN_FLOW_ANNOT);
        return Optional.ofNullable(this.inflowPolicy);
    }

    public Optional<FunctionSymbol> getOutFlowPolicy() {
        if (this.outflowPolicy != null) {
            return Optional.of(this.outflowPolicy);
        }

        this.outflowPolicy = getPolicy(Names.POLICY_OUT_FLOW_ANNOT);
        return Optional.ofNullable(this.outflowPolicy);
    }

    public Optional<FunctionSymbol> getFaultFlowPolicy() {
        if (this.faultflowPolicy != null) {
            return Optional.of(this.faultflowPolicy);
        }

        this.faultflowPolicy = getPolicy(Names.POLICY_FAULT_FLOW_ANNOT);
        return Optional.ofNullable(this.faultflowPolicy);
    }

    private FunctionSymbol getPolicy(String policyKind) {
        for (FunctionSymbol fn : getPublicFns()) {
            if (fn.annotations().isEmpty()) {
                continue;
            }

            if (isPolicyFunction(fn.annotations(), policyKind)) {
                return fn;
            }
        }

        return null;
    }

    private List<FunctionSymbol> getPublicFns() {
        if (this.publicFns != null) {
            return this.publicFns;
        }

        SemanticModel model = this.project.currentPackage().getDefaultModule().getCompilation().getSemanticModel();
        this.publicFns = model.moduleSymbols().stream()
                .filter(sym -> sym.kind() == SymbolKind.FUNCTION
                        && ((FunctionSymbol) sym).qualifiers().contains(Qualifier.PUBLIC))
                .map(fn -> (FunctionSymbol) fn)
                .collect(Collectors.toList());

        return this.publicFns;
    }

    private boolean isPolicyFunction(List<AnnotationSymbol> annots, String policyKind) {
        for (AnnotationSymbol annot : annots) {
            ModuleID id = annot.getModule().get().id();

            if (Names.BUILTIN_POLICY_ORG.equals(id.orgName())
                    && Names.POLICY_VALIDATOR_PKG.equals(id.packageName())
                    && policyKind.equals(annot.getName().get())) {
                return true;
            }
        }

        return false;
    }
}
