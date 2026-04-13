/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.engine.core.job;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class DataSourceConnectivityCheckResult implements Serializable {
    private String catalogIdentifier;
    private ExecutionMode executionMode = ExecutionMode.MASTER_ONLY;
    private boolean allSuccess;
    private long maxElapsedMs;
    private int timeoutMs;
    private List<MemberResult> memberResults = new ArrayList<>();

    public enum ExecutionMode {
        MASTER_ONLY,
        ALL_MEMBERS,
        SPECIFIC_MEMBER
    }

    @Data
    public static class MemberResult implements Serializable {
        private String address;
        private boolean success;
        private String error;
        private long elapsedMs;
    }

    public DataSourceConnectivityCheckResult() {}

    public boolean isSuccess() {
        return allSuccess;
    }

    public long getElapsedMs() {
        return maxElapsedMs;
    }

    public void addMemberResult(MemberResult memberResult) {
        if (memberResult == null) {
            return;
        }
        memberResults.add(memberResult);
        recomputeSummary();
    }

    public List<String> getFailedMembers() {
        return memberResults.stream()
                .filter(r -> !r.isSuccess())
                .map(MemberResult::getAddress)
                .collect(Collectors.toList());
    }

    public List<String> getAllMembers() {
        return memberResults.stream().map(MemberResult::getAddress).collect(Collectors.toList());
    }

    public String getLastError() {
        return memberResults.stream()
                .filter(r -> !r.isSuccess() && r.getError() != null)
                .map(MemberResult::getError)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public void recomputeSummary() {
        this.allSuccess = memberResults.stream().allMatch(MemberResult::isSuccess);
        this.maxElapsedMs =
                memberResults.stream().mapToLong(MemberResult::getElapsedMs).max().orElse(0L);
    }
}
