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

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ProtobufDeserializationSchemaTest {

    @Test
    void testTableIdShouldBeTablePathString()
            throws Descriptors.DescriptorValidationException, IOException, InterruptedException {
        String protoContent =
                "syntax = \"proto3\";\n"
                        + "package org.apache.seatunnel.format.protobuf;\n"
                        + "message Person {\n"
                        + "  string name = 1;\n"
                        + "}";
        String messageName = "Person";
        Descriptors.Descriptor descriptor =
                CompileDescriptor.compileDescriptorTempFile(protoContent, messageName);

        DynamicMessage dynamicMessage =
                DynamicMessage.newBuilder(descriptor)
                        .setField(descriptor.findFieldByName("name"), "alice")
                        .build();

        TablePath tablePath = TablePath.of("default", "protobuf_source");
        CatalogTable catalogTable =
                CatalogTable.of(
                        TableIdentifier.of("kafka", tablePath),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "name",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        buildOptions(protoContent, messageName),
                        Collections.emptyList(),
                        null);

        ProtobufDeserializationSchema schema = new ProtobufDeserializationSchema(catalogTable);
        SeaTunnelRow row = schema.deserialize(dynamicMessage.toByteArray());

        Assertions.assertEquals(tablePath.toString(), row.getTableId());
        Assertions.assertFalse(row.getTableId().startsWith("Optional["));
    }

    private Map<String, String> buildOptions(String protoContent, String messageName) {
        Map<String, String> options = new HashMap<>();
        options.put("protobuf_schema", protoContent);
        options.put("protobuf_message_name", messageName);
        return options;
    }
}
