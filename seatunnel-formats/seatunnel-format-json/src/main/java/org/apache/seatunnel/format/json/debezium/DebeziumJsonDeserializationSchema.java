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

package org.apache.seatunnel.format.json.debezium;

import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;

import org.apache.seatunnel.api.serialization.DeserializationSchema;
import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonError;
import org.apache.seatunnel.format.json.JsonDeserializationSchema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

public class DebeziumJsonDeserializationSchema implements DeserializationSchema<SeaTunnelRow> {
    private static final long serialVersionUID = 1L;

    private static final String OP_READ = "r"; // snapshot read
    private static final String OP_CREATE = "c"; // insert
    private static final String OP_UPDATE = "u"; // update
    private static final String OP_DELETE = "d"; // delete

    private static final String REPLICA_IDENTITY_EXCEPTION =
            "The \"before\" field of %s operation is null, "
                    + "if you are using Debezium Postgres Connector, "
                    + "please check the Postgres table has been set REPLICA IDENTITY to FULL level.";

    public static final String FORMAT = "Debezium";

    private final SeaTunnelRowType rowType;

    private final JsonDeserializationSchema jsonDeserializer;

    private final DebeziumRowConverter debeziumRowConverter;

    private final boolean ignoreParseErrors;

    private final boolean debeziumEnabledSchema;

    public DebeziumJsonDeserializationSchema(SeaTunnelRowType rowType, boolean ignoreParseErrors) {
        this.rowType = rowType;
        this.ignoreParseErrors = ignoreParseErrors;
        this.jsonDeserializer =
                new JsonDeserializationSchema(false, ignoreParseErrors, createJsonRowType(rowType));
        this.debeziumRowConverter = new DebeziumRowConverter(rowType);
        this.debeziumEnabledSchema = false;
    }

    public DebeziumJsonDeserializationSchema(
            SeaTunnelRowType rowType, boolean ignoreParseErrors, boolean debeziumEnabledSchema) {
        this.rowType = rowType;
        this.ignoreParseErrors = ignoreParseErrors;
        this.jsonDeserializer =
                new JsonDeserializationSchema(false, ignoreParseErrors, createJsonRowType(rowType));
        this.debeziumRowConverter = new DebeziumRowConverter(rowType);
        this.debeziumEnabledSchema = debeziumEnabledSchema;
    }

    @Override
    public SeaTunnelRow deserialize(byte[] message) throws IOException {
        throw new UnsupportedOperationException(
                "Please invoke DeserializationSchema#deserialize(byte[], Collector<SeaTunnelRow>) instead.");
    }

    // todo: make deserialize return list
    public List<SeaTunnelRow> deserializeList(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            // skip tombstone messages
            return Collections.emptyList();
        }

        try {
            JsonNode payload = getPayload(convertBytes(message));
            String op = payload.get("op").asText();

            if (OP_CREATE.equals(op) || OP_READ.equals(op)) {
                SeaTunnelRow insert = convertJsonNode(payload.get("after"));
                insert.setRowKind(RowKind.INSERT);
                return Collections.singletonList(insert);
            } else if (OP_UPDATE.equals(op)) {
                SeaTunnelRow before = convertJsonNode(payload.get("before"));
                if (before == null) {
                    throw new IllegalStateException(
                            String.format(REPLICA_IDENTITY_EXCEPTION, "UPDATE"));
                }
                before.setRowKind(RowKind.UPDATE_BEFORE);

                SeaTunnelRow after = convertJsonNode(payload.get("after"));
                after.setRowKind(RowKind.UPDATE_AFTER);

                return Lists.newArrayList(before, after);
            } else if (OP_DELETE.equals(op)) {
                SeaTunnelRow delete = convertJsonNode(payload.get("before"));
                if (delete == null) {
                    throw new IllegalStateException(
                            String.format(REPLICA_IDENTITY_EXCEPTION, "UPDATE"));
                }
                delete.setRowKind(RowKind.DELETE);

                return Collections.singletonList(delete);
            } else {
                throw new IllegalStateException(format("Unknown operation type '%s'.", op));
            }
        } catch (RuntimeException e) {
            // a big try catch to protect the processing.
            if (!ignoreParseErrors) {
                throw CommonError.jsonOperationError(FORMAT, new String(message), e);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void deserialize(byte[] message, Collector<SeaTunnelRow> out) throws IOException {
        deserializeList(message).forEach(out::collect);
    }

    private JsonNode getPayload(JsonNode jsonNode) {
        if (debeziumEnabledSchema) {
            return jsonNode.get("payload");
        }
        return jsonNode;
    }

    private JsonNode convertBytes(byte[] message) {
        try {
            return jsonDeserializer.deserializeToJsonNode(message);
        } catch (IOException t) {
            throw CommonError.jsonOperationError(FORMAT, new String(message), t);
        }
    }

    private SeaTunnelRow convertJsonNode(JsonNode root) {
        return debeziumRowConverter.serializeValue(root);
    }

    @Override
    public SeaTunnelDataType<SeaTunnelRow> getProducedType() {
        return this.rowType;
    }

    private static SeaTunnelRowType createJsonRowType(SeaTunnelRowType databaseSchema) {
        return databaseSchema;
    }
}
