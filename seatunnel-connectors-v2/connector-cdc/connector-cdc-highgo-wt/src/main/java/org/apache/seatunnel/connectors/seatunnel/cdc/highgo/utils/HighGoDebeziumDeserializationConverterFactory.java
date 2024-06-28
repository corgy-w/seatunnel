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

package org.apache.seatunnel.connectors.seatunnel.cdc.highgo.utils;

import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.connectors.cdc.debezium.DebeziumDeserializationConverter;
import org.apache.seatunnel.connectors.cdc.debezium.DebeziumDeserializationConverterFactory;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import com.highgo.jdbc.geometric.PGpoint;
import io.debezium.data.geometry.Geography;
import io.debezium.data.geometry.Geometry;
import io.debezium.data.geometry.Point;
import io.debezium.util.HexConverter;

import java.time.ZoneId;
import java.util.Optional;

public class HighGoDebeziumDeserializationConverterFactory
        implements DebeziumDeserializationConverterFactory {
    public static final DebeziumDeserializationConverterFactory INSTANCE =
            new HighGoDebeziumDeserializationConverterFactory();

    @Override
    public Optional<DebeziumDeserializationConverter> createUserDefinedConverter(
            SeaTunnelDataType<?> type, ZoneId serverTimeZone) {
        switch (type.getSqlType()) {
            case STRING:
                return Optional.of(
                        new DebeziumDeserializationConverter() {

                            @Override
                            public Object convert(Object dbzObj, Schema schema) throws Exception {
                                if (dbzObj instanceof Struct) {
                                    Struct struct = (Struct) dbzObj;
                                    switch (schema.name()) {
                                        case Point.LOGICAL_NAME:
                                            return new PGpoint(
                                                            struct.getFloat64(Point.X_FIELD),
                                                            struct.getFloat64(Point.Y_FIELD))
                                                    .getValue();
                                        case Geometry.LOGICAL_NAME:
                                        case Geography.LOGICAL_NAME:
                                            return HexConverter.convertToHexString(
                                                    struct.getBytes(Geometry.WKB_FIELD));
                                        default:
                                            return dbzObj.toString();
                                    }
                                }
                                return dbzObj.toString();
                            }
                        });
        }
        return Optional.empty();
    }
}
