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

package org.apache.seatunnel.transform.dmleventfilter;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;

import lombok.NonNull;
import lombok.ToString;

import java.util.List;
import java.util.Set;

@ToString(of = {"includeKinds", "excludeKinds"})
public class DMLEventFilterTransform implements SeaTunnelTransform<SeaTunnelRow> {
    public static String PLUGIN_NAME = "DMLEventFilter";

    private final Set<RowKind> includeKinds;
    private final Set<RowKind> excludeKinds;

    private List<CatalogTable> inputCatalogTable;

    public DMLEventFilterTransform(
            @NonNull DMLEventFilterTransformConfig config,
            @NonNull List<CatalogTable> inputCatalogTable) {
        includeKinds = config.getIncludeKinds();
        excludeKinds = config.getExcludeKinds();
        this.inputCatalogTable = inputCatalogTable;
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        return inputCatalogTable;
    }

    @Override
    public SeaTunnelRow map(SeaTunnelRow row) {
        if (!this.excludeKinds.isEmpty()) {
            return this.excludeKinds.contains(row.getRowKind()) ? null : row;
        }
        if (!this.includeKinds.isEmpty()) {
            Set<RowKind> includeKinds = this.includeKinds;
            return includeKinds.contains(row.getRowKind()) ? row : null;
        }
        return row;
    }

    @Override
    public void setTypeInfo(SeaTunnelDataType<SeaTunnelRow> inputDataType) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public CatalogTable getProducedCatalogTable() {
        return inputCatalogTable.get(0);
    }
}
