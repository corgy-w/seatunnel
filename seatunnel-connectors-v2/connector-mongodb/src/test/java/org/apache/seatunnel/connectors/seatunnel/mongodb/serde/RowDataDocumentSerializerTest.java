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

package org.apache.seatunnel.connectors.seatunnel.mongodb.serde;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.mongodb.sink.MongodbWriterOptions;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import java.util.function.Function;

public class RowDataDocumentSerializerTest {

    @Test
    public void testReadRowKindWithUpsertEnabled() {
        // Test that RowKind.READ uses upsertSupplier when upsert is enabled
        SeaTunnelRowType rowType =
                new SeaTunnelRowType(
                        new String[] {"id", "name"},
                        new BasicType[] {BasicType.INT_TYPE, BasicType.STRING_TYPE});
        RowDataToBsonConverters.RowDataToBsonConverter converter =
                new RowDataToBsonConverters().createConverter(rowType);

        MongodbWriterOptions options =
                new MongodbWriterOptions(
                        "mongodb://localhost:27017",
                        "test_db",
                        "test_collection",
                        1000,
                        30000L,
                        true, // upsert enabled
                        new String[] {"id"},
                        3,
                        1000L,
                        false,
                        null,
                        null);

        Function<BsonDocument, BsonDocument> filterConditions = doc -> new BsonDocument();

        RowDataDocumentSerializer serializer =
                new RowDataDocumentSerializer(converter, options, filterConditions);

        // Create a SeaTunnelRow with RowKind.READ
        SeaTunnelRow row = new SeaTunnelRow(new Object[] {1, "test"});
        row.setRowKind(RowKind.READ);
        row.setTableId("test_table");

        // Verify that serializeToWriteModel returns UpdateOneModel (upsert)
        WriteModel<BsonDocument> writeModel = serializer.serializeToWriteModel(row);
        Assertions.assertInstanceOf(
                UpdateOneModel.class,
                writeModel,
                "RowKind.READ should use UpdateOneModel when upsert is enabled");
    }

    @Test
    public void testReadRowKindWithUpsertDisabled() {
        // Test that RowKind.READ uses insertSupplier when upsert is disabled
        SeaTunnelRowType rowType =
                new SeaTunnelRowType(
                        new String[] {"id", "name"},
                        new BasicType[] {BasicType.INT_TYPE, BasicType.STRING_TYPE});
        RowDataToBsonConverters.RowDataToBsonConverter converter =
                new RowDataToBsonConverters().createConverter(rowType);

        MongodbWriterOptions options =
                new MongodbWriterOptions(
                        "mongodb://localhost:27017",
                        "test_db",
                        "test_collection",
                        1000,
                        30000L,
                        false, // upsert disabled
                        null,
                        3,
                        1000L,
                        false,
                        null,
                        null);

        Function<BsonDocument, BsonDocument> filterConditions = doc -> new BsonDocument();

        RowDataDocumentSerializer serializer =
                new RowDataDocumentSerializer(converter, options, filterConditions);

        // Create a SeaTunnelRow with RowKind.READ
        SeaTunnelRow row = new SeaTunnelRow(new Object[] {1, "test"});
        row.setRowKind(RowKind.READ);
        row.setTableId("test_table");

        // Verify that serializeToWriteModel returns InsertOneModel
        WriteModel<BsonDocument> writeModel = serializer.serializeToWriteModel(row);
        Assertions.assertInstanceOf(
                InsertOneModel.class,
                writeModel,
                "RowKind.READ should use InsertOneModel when upsert is disabled");
    }

    @Test
    public void testAllSupportedRowKinds() {
        // Test that all RowKind values are supported
        SeaTunnelRowType rowType =
                new SeaTunnelRowType(
                        new String[] {"id", "name"},
                        new BasicType[] {BasicType.INT_TYPE, BasicType.STRING_TYPE});
        RowDataToBsonConverters.RowDataToBsonConverter converter =
                new RowDataToBsonConverters().createConverter(rowType);

        MongodbWriterOptions options =
                new MongodbWriterOptions(
                        "mongodb://localhost:27017",
                        "test_db",
                        "test_collection",
                        1000,
                        30000L,
                        true,
                        new String[] {"id"},
                        3,
                        1000L,
                        false,
                        null,
                        null);

        Function<BsonDocument, BsonDocument> filterConditions = doc -> new BsonDocument();

        RowDataDocumentSerializer serializer =
                new RowDataDocumentSerializer(converter, options, filterConditions);

        // Test all supported RowKinds
        RowKind[] supportedRowKinds = {
            RowKind.INSERT, RowKind.UPDATE_AFTER, RowKind.DELETE, RowKind.READ
        };

        for (RowKind rowKind : supportedRowKinds) {
            SeaTunnelRow row = new SeaTunnelRow(new Object[] {1, "test"});
            row.setRowKind(rowKind);
            row.setTableId("test_table");

            // Should not throw exception for supported RowKinds
            WriteModel<BsonDocument> writeModel = serializer.serializeToWriteModel(row);
            Assertions.assertNotNull(
                    writeModel,
                    "serializeToWriteModel should return a WriteModel for RowKind: " + rowKind);
        }
    }
}
