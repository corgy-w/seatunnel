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

package org.apache.seatunnel.transform.mapper;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;
import org.apache.seatunnel.transform.exception.TransformCommonError;
import org.apache.seatunnel.transform.exception.TransformExceptionUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MapperMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public MapperMultiCatalogTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return Optional.of(new MapperTransform(config, inputCatalogTable));
    }

    @Override
    public String getPluginName() {
        return MapperTransform.PLUGIN_NAME;
    }

    @Override
    protected void preCheckConfig(ReadonlyConfig config) {
        List<MapperConfig.SpecificModify> specificModifies = config.get(MapperConfig.SPECIFIC);
        if (CollectionUtils.isEmpty(specificModifies)) {
            return;
        }

        TransformExceptionUtil.withErrorCheck(
                getPluginName(),
                specificModifies.iterator(),
                modify -> {
                    TablePath configPath = TablePath.of(modify.getInputName(), true);
                    boolean found =
                            inputCatalogTables.stream()
                                    .anyMatch(
                                            table -> {
                                                TablePath inputPath =
                                                        table.getTableId().toTablePath();
                                                return inputPath
                                                                .getTableName()
                                                                .equals(configPath.getTableName())
                                                        && (StringUtils.isBlank(
                                                                        configPath.getSchemaName())
                                                                || Objects.equals(
                                                                        inputPath.getSchemaName(),
                                                                        configPath.getSchemaName()))
                                                        && (StringUtils.isBlank(
                                                                        configPath
                                                                                .getDatabaseName())
                                                                || Objects.equals(
                                                                        inputPath.getDatabaseName(),
                                                                        configPath
                                                                                .getDatabaseName()));
                                            });

                    if (!found) {
                        throw TransformCommonError.cannotFindInputTableError(
                                getPluginName(), modify.getInputName());
                    }
                });
    }
}
