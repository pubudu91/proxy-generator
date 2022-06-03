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

import java.util.Objects;

public class PackageID {

    private final String org;
    private final String name;
    private final String version;

    private PackageID(String org, String name, String version) {
        this.org = org;
        this.name = name;
        this.version = version;
    }

    public static PackageID from(String policyName, String version) {
        String[] split = policyName.split("/");

        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid policy name format: " + policyName);
        }

        return new PackageID(split[0], split[1], version);
    }

    public String org() {
        return this.org;
    }

    public String name() {
        return this.name;
    }

    public String version() {
        return this.version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PackageID packageID = (PackageID) o;
        return org.equals(packageID.org) && name.equals(packageID.name) && version.equals(packageID.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(org, name, version);
    }
}
