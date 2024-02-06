/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.dws.guassdb.sink.writer;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DwsGaussDBMemoryTable {

    private final Map<String, SeaTunnelRow> upsertRows = new ConcurrentHashMap<>();

    private final Map<String, SeaTunnelRow> deleteRows = new ConcurrentHashMap<>();

    private final SeaTunnelRowType seaTunnelRowType;

    private final Function<SeaTunnelRow, String> primaryKeyExtractor;

    public DwsGaussDBMemoryTable(SeaTunnelRowType seaTunnelRowType, String primaryKey) {
        this.seaTunnelRowType = seaTunnelRowType;
        this.primaryKeyExtractor = getPrimaryKeyExtractor(seaTunnelRowType, primaryKey);
    }

    public void write(SeaTunnelRow element) {
        String primaryKey = primaryKeyExtractor.apply(element);
        switch (element.getRowKind()) {
            case UPDATE_BEFORE:
                // todo: how to deal with update before
                break;
            case INSERT:
            case UPDATE_AFTER:
                deleteRows.remove(primaryKey);
                upsertRows.put(primaryKey, element);
                break;
            case DELETE:
                upsertRows.remove(primaryKey);
                deleteRows.put(primaryKey, element);
                break;
        }
    }

    public Collection<SeaTunnelRow> getDeleteRows() {
        return deleteRows.values();
    }

    public Collection<SeaTunnelRow> getUpsertRows() {
        return upsertRows.values();
    }

    public int size() {
        return deleteRows.size() + upsertRows.size();
    }

    public void truncate() {
        upsertRows.clear();
        deleteRows.clear();
    }

    private Function<SeaTunnelRow, String> getPrimaryKeyExtractor(
            SeaTunnelRowType seaTunnelRowType, String primaryKey) {
        if (StringUtils.isEmpty(primaryKey)) {
            throw new IllegalArgumentException("The primary key is empty");
        }
        String[] fieldNames = seaTunnelRowType.getFieldNames();
        int primaryKeyIndex = -1;
        for (int j = 0; j < fieldNames.length; j++) {
            if (primaryKey.equals(fieldNames[j])) {
                primaryKeyIndex = j;
                break;
            }
        }
        if (primaryKeyIndex == -1) {
            throw new IllegalArgumentException(
                    "The primary key: " + primaryKey + " in not exist in the table");
        }
        final int finalPrimaryKeyIndex = primaryKeyIndex;
        return (row) -> String.valueOf(row.getField(finalPrimaryKeyIndex));
    }
}
