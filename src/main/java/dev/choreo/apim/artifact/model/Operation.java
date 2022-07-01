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

package dev.choreo.apim.artifact.model;

import dev.choreo.apim.artifact.model.AttachedPolicies;

public class Operation {

    private String id;
    private String target;
    private String verb;
    private AttachedPolicies operationPolicies = new AttachedPolicies();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public AttachedPolicies getOperationPolicies() {
        return operationPolicies;
    }

    public void setOperationPolicies(AttachedPolicies operationPolicies) {
        this.operationPolicies = operationPolicies;
    }
}
