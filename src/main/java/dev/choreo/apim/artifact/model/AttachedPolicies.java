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

import java.util.List;

public class AttachedPolicies {

    private List<Policy> request;
    private List<Policy> response;
    private List<Policy> fault;

    public List<Policy> getRequest() {
        return request;
    }

    public void setRequest(List<Policy> request) {
        this.request = request;
    }

    public List<Policy> getResponse() {
        return response;
    }

    public void setResponse(List<Policy> response) {
        this.response = response;
    }

    public List<Policy> getFault() {
        return fault;
    }

    public void setFault(List<Policy> fault) {
        this.fault = fault;
    }
}
