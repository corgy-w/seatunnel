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

package org.apache.seatunnel.connectors.dolphindb.catalog;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import java.util.List;

@Disabled
class DolphinDBCatalogTest {

    public DolphinDBCatalog dolphinDBCatalog;

    @BeforeEach
    public void before() {
        dolphinDBCatalog =
                new DolphinDBCatalog(
                        "dolphindb",
                        Lists.newArrayList("localhost:8848"),
                        "admin",
                        "123456",
                        "dfs://whalescheduler",
                        "user",
                        DolphinDBConfig.SAVE_MODE_CREATE_TEMPLATE.defaultValue(),
                        false,
                        null);
        dolphinDBCatalog.open();
    }

    @AfterEach
    public void after() {
        dolphinDBCatalog.close();
    }

    @Test
    void getDefaultDatabase() {
        String defaultDatabase = dolphinDBCatalog.getDefaultDatabase();
        Assertions.assertEquals("dfs://whalescheduler", defaultDatabase);
    }

    @Test
    void databaseExists() {
        Assertions.assertTrue(dolphinDBCatalog.databaseExists("dfs://whalescheduler"));
    }

    @Test
    void listDatabases() {
        List<String> listDatabases = dolphinDBCatalog.listDatabases();
        Assertions.assertTrue(listDatabases.contains("dfs://whalescheduler"));
    }

    @Test
    void listTables() {
        List<String> listTables = dolphinDBCatalog.listTables("dfs://whalescheduler");
        Assertions.assertTrue(listTables.contains("user"));
    }

    @Test
    void tableExists() {
        TablePath tablePath = new TablePath("dfs://whalescheduler", null, "user");
        Assertions.assertTrue(dolphinDBCatalog.tableExists(tablePath));
    }

    @Test
    void getTable() {
        TablePath tablePath = new TablePath("dfs://whalescheduler", null, "user");
        CatalogTable dbCatalogTable = dolphinDBCatalog.getTable(tablePath);
        System.out.println(dbCatalogTable);
    }

    @Test
    void createTable() {}

    @Test
    void dropTable() {}

    @Test
    void createDatabase() {}

    @Test
    void dropDatabase() {}

    @Test
    void dropTableInternal() {}

    @Test
    void truncateTable() {
        dolphinDBCatalog.truncateTable(new TablePath("dfs://whalescheduler", null, "user"), false);
    }
}
