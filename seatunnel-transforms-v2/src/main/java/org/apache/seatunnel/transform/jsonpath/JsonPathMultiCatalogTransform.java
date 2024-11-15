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

package org.apache.seatunnel.transform.jsonpath;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;
import org.apache.seatunnel.transform.exception.TransformCommonError;
import org.apache.seatunnel.transform.exception.TransformExceptionUtil;
import org.apache.seatunnel.transform.sql.SQLTransform;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.seatunnel.transform.jsonpath.JsonPathTransformConfig.MULTI_TABLES;
import static org.apache.seatunnel.transform.jsonpath.JsonPathTransformConfig.SRC_FIELD;

public class JsonPathMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public JsonPathMultiCatalogTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    public String getPluginName() {
        return JsonPathTransform.PLUGIN_NAME;
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return JsonPathTransformConfig.ofOptional(config, inputCatalogTable)
                .map(
                        jsonPathTransformConfig ->
                                new JsonPathTransform(jsonPathTransformConfig, inputCatalogTable));
    }

    @Override
    protected void preCheckConfig(ReadonlyConfig config) {
        final List<JsonPathTransformConfig.TableTransforms> tableTransforms =
                config.get(MULTI_TABLES);
        if (CollectionUtils.isEmpty(tableTransforms)) {
            return;
        }
        final List<String> fullTableNameList =
                inputCatalogTables.stream()
                        .map(table -> table.getTablePath().getFullName())
                        .collect(Collectors.toList());
        TransformExceptionUtil.withErrorCheck(
                this.getPluginName(),
                config.get(MULTI_TABLES).iterator(),
                entry -> {
                    final String tablePath = entry.getTablePath();
                    if (!fullTableNameList.contains(tablePath)) {
                        throw TransformCommonError.cannotFindInputTableError(
                                SQLTransform.PLUGIN_NAME, tablePath);
                    }
                    CatalogTable sourceTable =
                            inputCatalogTables.stream()
                                    .filter(
                                            table ->
                                                    table.getTablePath()
                                                            .getFullName()
                                                            .equals(tablePath))
                                    .findFirst()
                                    .get();
                    List<Map<String, Object>> columns = entry.getColumns();
                    for (Map<String, Object> map : columns) {
                        ReadonlyConfig subConfig = ReadonlyConfig.fromMap(map);
                        String srcField = subConfig.get(SRC_FIELD);
                        if (!sourceTable.getTableSchema().contains(srcField)) {
                            throw TransformCommonError.cannotFindInputTableFieldError(
                                    this.getPluginName(), tablePath, srcField);
                        }
                    }
                });
    }
}
