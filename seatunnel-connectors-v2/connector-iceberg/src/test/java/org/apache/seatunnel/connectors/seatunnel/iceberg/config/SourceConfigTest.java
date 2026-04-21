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

package org.apache.seatunnel.connectors.seatunnel.iceberg.config;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourceConfigTest {

    @Test
    void shouldNormalizeWhereConditionForSingleTable() {
        Config config =
                ConfigFactory.parseString(
                        "catalog_name = \"seatunnel\"\n"
                                + "namespace = \"db1\"\n"
                                + "table = \"t1\"\n"
                                + "where_condition = \"id > 100\"\n"
                                + "iceberg.catalog.config = {\n"
                                + "  type = \"hadoop\"\n"
                                + "  warehouse = \"file:///tmp/warehouse\"\n"
                                + "}\n");

        SourceConfig sourceConfig = new SourceConfig(ReadonlyConfig.fromConfig(config));

        Assertions.assertEquals(1, sourceConfig.getTableList().size());
        Assertions.assertEquals(
                "where id > 100", sourceConfig.getTableList().get(0).getWhereCondition());
    }

    @Test
    void shouldApplyWhereConditionToAllTables() {
        Config config =
                ConfigFactory.parseString(
                        "catalog_name = \"seatunnel\"\n"
                                + "namespace = \"db1\"\n"
                                + "where_condition = \"where dt = '2024-01-01'\"\n"
                                + "iceberg.catalog.config = {\n"
                                + "  type = \"hadoop\"\n"
                                + "  warehouse = \"file:///tmp/warehouse\"\n"
                                + "}\n"
                                + "table_list = [\n"
                                + "  {\n"
                                + "    table = \"t1\"\n"
                                + "  },\n"
                                + "  {\n"
                                + "    table = \"t2\"\n"
                                + "  }\n"
                                + "]\n");

        SourceConfig sourceConfig = new SourceConfig(ReadonlyConfig.fromConfig(config));

        Assertions.assertEquals(2, sourceConfig.getTableList().size());
        sourceConfig
                .getTableList()
                .forEach(
                        tableConfig ->
                                Assertions.assertEquals(
                                        "where dt = '2024-01-01'",
                                        tableConfig.getWhereCondition()));
    }
}
