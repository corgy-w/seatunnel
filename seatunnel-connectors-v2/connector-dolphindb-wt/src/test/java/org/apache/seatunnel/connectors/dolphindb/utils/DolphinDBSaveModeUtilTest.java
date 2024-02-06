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

package org.apache.seatunnel.connectors.dolphindb.utils;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DolphinDBSaveModeUtilTest {

    @Test
    void fillingCreateSql() {
        List<Column> columns = new ArrayList<>();

        columns.add(PhysicalColumn.of("id", BasicType.LONG_TYPE, (Long) null, true, null, ""));
        columns.add(PhysicalColumn.of("name", BasicType.STRING_TYPE, (Long) null, true, null, ""));
        columns.add(PhysicalColumn.of("age", BasicType.INT_TYPE, (Long) null, true, null, ""));
        columns.add(PhysicalColumn.of("gender", BasicType.BYTE_TYPE, (Long) null, true, null, ""));
        columns.add(
                PhysicalColumn.of("create_time", BasicType.LONG_TYPE, (Long) null, true, null, ""));

        String result =
                DolphinDBSaveModeUtil.fillingCreateSql(
                        "CREATE TABLE '${database}'.'${table_name}'\" (                                                                                                                                                   \n"
                                + "${rowtype_primary_key}  ,       \n"
                                + "create_time TIMESTAMP,  \n"
                                + "${rowtype_fields}"
                                + ")\n"
                                + "partitioned BY ${rowtype_primary_key} ;",
                        "dfs://whalescheduler",
                        "partition_table2",
                        TableSchema.builder()
                                .primaryKey(PrimaryKey.of("", Arrays.asList("id")))
                                .columns(columns)
                                .build());

        System.out.println(result);
    }
}
