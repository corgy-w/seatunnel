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

package io.debezium.connector.oracle;

import io.debezium.connector.oracle.oracleAgent.OracleAgentDmlEntry;
import io.debezium.data.Envelope;
import io.debezium.pipeline.spi.OffsetContext;
import io.debezium.relational.RelationalChangeRecordEmitter;
import io.debezium.util.Clock;

import static com.google.common.base.Preconditions.checkNotNull;

public class OracleDataChangeRecordEmitter extends RelationalChangeRecordEmitter {

    private final OracleAgentDmlEntry oracleAgentDmlEntry;

    public OracleDataChangeRecordEmitter(
            OffsetContext offsetContext, Clock clock, OracleAgentDmlEntry oracleAgentDmlEntry) {
        super(offsetContext, clock);
        this.oracleAgentDmlEntry =
                checkNotNull(oracleAgentDmlEntry, "oracleAgentDmlEntry must not be null");
    }

    @Override
    protected Envelope.Operation getOperation() {
        switch (oracleAgentDmlEntry.getOperation()) {
            case INSERT:
                return Envelope.Operation.CREATE;
            case UPDATE:
                return Envelope.Operation.UPDATE;
            case DELETE:
                return Envelope.Operation.DELETE;
            default:
                throw new IllegalArgumentException(
                        "Received event of unexpected command type: "
                                + oracleAgentDmlEntry.getOperation());
        }
    }

    @Override
    protected Object[] getOldColumnValues() {
        return oracleAgentDmlEntry.getOldValues();
    }

    @Override
    protected Object[] getNewColumnValues() {
        return oracleAgentDmlEntry.getNewValues();
    }
}
