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

package org.apache.seatunnel.transform.common;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelFlatMapTransform;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.exception.ErrorDataTransformException;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Abstract base class for transforms that support multiple catalog tables and implement flatMap
 * (one-to-many) transformation.
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Multi-table routing based on tableId
 *   <li>Per-table transform instance management
 *   <li>Error handling with configurable skip behavior
 * </ul>
 */
@Slf4j
public abstract class AbstractCatalogSupportFlatMapTransform
        implements SeaTunnelFlatMapTransform<SeaTunnelRow> {

    protected final ErrorHandleWay rowErrorHandleWay;
    protected List<CatalogTable> inputCatalogTables;
    protected List<CatalogTable> outputCatalogTables;
    protected Map<String, SeaTunnelTransform<SeaTunnelRow>> transformMap;

    public AbstractCatalogSupportFlatMapTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        this.rowErrorHandleWay = config.get(CommonOptions.ROW_ERROR_HANDLE_WAY_OPTION);
        this.inputCatalogTables = inputCatalogTables;
        this.transformMap = new HashMap<>();
        preCheckConfig(config);
        inputCatalogTables.forEach(
                inputCatalogTable -> {
                    String tableId = inputCatalogTable.getTableId().toTablePath().toString();
                    buildTransform(inputCatalogTable, config)
                            .ifPresent(transform -> transformMap.put(tableId, transform));
                });

        this.outputCatalogTables =
                inputCatalogTables.stream()
                        .map(
                                inputCatalogTable -> {
                                    String tableName =
                                            inputCatalogTable.getTableId().toTablePath().toString();
                                    SeaTunnelTransform<SeaTunnelRow> transform =
                                            transformMap.get(tableName);
                                    // If no transform is defined, use the input catalog table as-is
                                    return transform != null
                                            ? transform.getProducedCatalogTable()
                                            : inputCatalogTable;
                                })
                        .collect(Collectors.toList());
    }

    @Override
    public List<SeaTunnelRow> flatMap(SeaTunnelRow row) {
        try {
            SeaTunnelTransform<SeaTunnelRow> transform;
            if (inputCatalogTables.size() == 1) {
                // Only one input table: directly get the single transform (may be null if
                // pass-through)
                transform =
                        transformMap.values().isEmpty()
                                ? null
                                : transformMap.values().iterator().next();
            } else {
                // Multiple input tables: route by tableId
                transform = transformMap.get(row.getTableId());
            }

            // If no transform is defined for this table, return the row as-is (pass-through)
            if (transform == null) {
                return Collections.singletonList(row);
            }

            // Check if the transform supports FlatMapTransform
            if (transform instanceof SeaTunnelFlatMapTransform) {
                return ((SeaTunnelFlatMapTransform<SeaTunnelRow>) transform).flatMap(row);
            } else {
                // Fallback to map() if available (for backward compatibility)
                SeaTunnelRow result = transform.map(row);
                return result == null ? Collections.emptyList() : Collections.singletonList(result);
            }
        } catch (ErrorDataTransformException e) {
            if (e.getErrorHandleWay() != null) {
                ErrorHandleWay errorHandleWay = e.getErrorHandleWay();
                if (errorHandleWay.allowSkip() || errorHandleWay.allowSkipThisRow()) {
                    log.debug("Skip row due to error", e);
                    return Collections.emptyList();
                }
                throw e;
            }
            if (rowErrorHandleWay.allowSkip()) {
                log.debug("Skip row due to error", e);
                return Collections.emptyList();
            }
            throw e;
        }
    }

    @Override
    public SeaTunnelRow map(SeaTunnelRow row) {
        List<SeaTunnelRow> result = flatMap(row);
        if (result == null || result.isEmpty()) {
            return null;
        }
        if (result.size() > 1) {
            throw new IllegalStateException(
                    "FlatMap transform returned multiple rows but map() was called. Please use flatMap() instead.");
        }
        return result.get(0);
    }

    protected abstract Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config);

    protected void preCheckConfig(ReadonlyConfig config) {}

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        return outputCatalogTables;
    }

    @Override
    public CatalogTable getProducedCatalogTable() {
        return outputCatalogTables.get(0);
    }

    @Override
    public void setTypeInfo(SeaTunnelDataType<SeaTunnelRow> inputDataType) {}
}
