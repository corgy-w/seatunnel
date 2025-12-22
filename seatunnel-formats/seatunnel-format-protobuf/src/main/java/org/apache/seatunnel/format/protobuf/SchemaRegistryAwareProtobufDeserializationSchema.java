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

package org.apache.seatunnel.format.protobuf;

import org.apache.seatunnel.api.serialization.DeserializationSchema;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
public class SchemaRegistryAwareProtobufDeserializationSchema
        implements DeserializationSchema<SeaTunnelRow> {

    private static final long serialVersionUID = -2134049729306615854L;

    private static final int MAX_ADDITIONAL_HEADER_BYTES = 16;

    private final ProtobufDeserializationSchema inner;
    private final SeaTunnelRowType rowType;

    public SchemaRegistryAwareProtobufDeserializationSchema(CatalogTable catalogTable) {
        this.inner = new ProtobufDeserializationSchema(catalogTable);
        this.rowType = catalogTable.getSeaTunnelRowType();
    }

    @Override
    public SeaTunnelRow deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return inner.deserialize(message);
        }

        int length = message.length;

        if (length >= 6 && message[0] == 0) {
            int candidateStart = 6;
            if (candidateStart < length) {
                try {
                    return inner.deserialize(Arrays.copyOfRange(message, candidateStart, length));
                } catch (IOException | RuntimeException ignored) {
                    log.warn("Protobuf message not recognized, falling back");
                }
            }

            int maxProbeStart = Math.min(5 + MAX_ADDITIONAL_HEADER_BYTES, length - 1);
            for (int start = 5; start <= maxProbeStart; start++) {
                if (start == candidateStart) {
                    continue;
                }
                try {
                    return inner.deserialize(Arrays.copyOfRange(message, start, length));
                } catch (IOException | RuntimeException ignored) {
                    log.warn("Protobuf message not recognized, falling back");
                }
            }
        }

        return inner.deserialize(message);
    }

    @Override
    public SeaTunnelDataType<SeaTunnelRow> getProducedType() {
        return this.rowType;
    }
}
