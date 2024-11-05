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

package org.apache.seatunnel.format.cdc.custom.json;

import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.DecimalFormat;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;

import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class DebeziumJsonConverter implements Serializable {
    public static final String SOURCE_CONNECTOR = "connector";
    public static final String SOURCE_CONNECTOR_ORACLE = "oracle";
    public static final String SOURCE_CONNECTOR_MYSQL = "mysql";
    public static final String SOURCE_CONNECTOR_POSTGRES = "postgresql";
    public static final String SOURCE_CONNECTOR_DAMENG = "dameng";
    public static final String SOURCE_DATABASE = "db";
    public static final String SOURCE_SCHEMA = "schema";
    public static final String SOURCE_TABLE = "table";
    public static final String SOURCE_TS_MS = "ts_ms";
    public static final String SOURCE_MYSQL_FILE = "file";
    public static final String SOURCE_MYSQL_POS = "pos";
    public static final String SOURCE_MYSQL_GTID = "gtid";
    public static final String SOURCE_ORACLE_SCN = "scn";
    public static final String SOURCE_DAMENG_SCN = "scn";
    public static final String SOURCE_POSTGRES_LSN = "lsn";
    public static final String OP_INSERT = "c";
    public static final String OP_UPDATE = "u";
    public static final String OP_DELETE = "d";
    public static final String OP_READ = "r";

    private final boolean keySchemaEnable;
    private final boolean valueSchemaEnable;
    private transient volatile JsonConverter keyConverter;
    private transient volatile JsonConverter valueConverter;

    public SchemaAndValue deserializeKey(String s) {
        tryInit();

        return keyConverter.toConnectData(null, s.getBytes());
    }

    public SchemaAndValue deserializeValue(String s) {
        tryInit();
        return valueConverter.toConnectData(null, s.getBytes());
    }

    private void tryInit() {
        if (keyConverter == null) {
            synchronized (this) {
                if (keyConverter == null) {
                    keyConverter = new JsonConverter();
                    Map<String, Object> configs = new HashMap<>();
                    configs.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, keySchemaEnable);
                    configs.put(
                            JsonConverterConfig.DECIMAL_FORMAT_CONFIG,
                            DecimalFormat.NUMERIC.name());
                    keyConverter.configure(configs, true);
                }
            }
        }
        if (valueConverter == null) {
            synchronized (this) {
                if (valueConverter == null) {
                    valueConverter = new JsonConverter();
                    Map<String, Object> configs = new HashMap<>();
                    configs.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, valueSchemaEnable);
                    configs.put(
                            JsonConverterConfig.DECIMAL_FORMAT_CONFIG,
                            DecimalFormat.NUMERIC.name());
                    valueConverter.configure(configs, false);
                }
            }
        }
    }
}
