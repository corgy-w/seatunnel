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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.highgo;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.psql.PostgresCreateTableSqlBuilder;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.highgo.HighGoTypeConverter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class HighGoCreateTableSqlBuilder extends PostgresCreateTableSqlBuilder {
    private static final List<String> COMPATIBLE_DATABASES =
            Arrays.asList(
                    DatabaseIdentifier.POSTGRESQL.toUpperCase(),
                    DatabaseIdentifier.HIGHGO.toUpperCase());

    public HighGoCreateTableSqlBuilder(CatalogTable catalogTable) {
        super(catalogTable);
    }

    public HighGoCreateTableSqlBuilder(CatalogTable catalogTable, Collection<String> pgPlugins) {
        super(catalogTable, pgPlugins);
    }

    @Override
    protected boolean isCompatibleCatalog(String sourceCatalogName) {
        return COMPATIBLE_DATABASES.contains(sourceCatalogName.toUpperCase());
    }

    @Override
    protected String buildColumnType(Column column) {
        return HighGoTypeConverter.INSTANCE.reconvert(column).getColumnType();
    }
}
