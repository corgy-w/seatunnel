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

package io.debezium.connector.oracle.oracleAgent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@ToString
@Accessors(chain = true)
@EqualsAndHashCode(of = {"operation", "newValues", "oldValues"})
@RequiredArgsConstructor
public class OracleAgentDmlEntryImpl implements OracleAgentDmlEntry {
    @NonNull private final Operation operation;
    private final Object[] newValues;
    private final Object[] oldValues;

    public static OracleAgentDmlEntryImpl forInsert(Object[] newColumnValues) {
        return new OracleAgentDmlEntryImpl(Operation.INSERT, newColumnValues, null);
    }

    public static OracleAgentDmlEntryImpl forUpdate(
            Object[] newColumnValues, Object[] oldColumnValues) {
        return new OracleAgentDmlEntryImpl(Operation.UPDATE, newColumnValues, oldColumnValues);
    }

    public static OracleAgentDmlEntryImpl forDelete(Object[] oldColumnValues) {
        return new OracleAgentDmlEntryImpl(Operation.DELETE, null, oldColumnValues);
    }
}
