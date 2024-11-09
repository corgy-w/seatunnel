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

import org.apache.seatunnel.shade.com.fasterxml.jackson.annotation.JsonAlias;
import org.apache.seatunnel.shade.com.fasterxml.jackson.core.type.TypeReference;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.SeaTunnelDataTypeConvertorUtil;
import org.apache.seatunnel.api.table.catalog.schema.ReadonlyConfigParser;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.transform.common.CommonOptions;
import org.apache.seatunnel.transform.common.ErrorHandleWay;
import org.apache.seatunnel.transform.exception.TransformCommonError;
import org.apache.seatunnel.transform.exception.TransformException;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.seatunnel.transform.exception.JsonPathTransformErrorCode.COLUMNS_MUST_NOT_EMPTY;
import static org.apache.seatunnel.transform.exception.JsonPathTransformErrorCode.DEST_FIELD_MUST_NOT_EMPTY;
import static org.apache.seatunnel.transform.exception.JsonPathTransformErrorCode.PATH_MUST_NOT_EMPTY;
import static org.apache.seatunnel.transform.exception.JsonPathTransformErrorCode.SRC_FIELD_MUST_NOT_EMPTY;

public class JsonPathTransformConfig implements Serializable {

    public static final Option<String> PATH =
            Options.key("path")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("JSONPath for Selecting Field from JSON.");

    public static final Option<String> SRC_FIELD =
            Options.key("src_field")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("JSON source field.");

    public static final Option<Boolean> DELETE_SRC_FIELD =
            Options.key("delete_src_field")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("JSON source field.");

    public static final Option<String> DEST_FIELD =
            Options.key("dest_field")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("output field.");

    public static final Option<String> DEST_TYPE =
            Options.key("dest_type")
                    .stringType()
                    .defaultValue("string")
                    .withDescription("output field type,default string");

    public static final Option<Map<String, Object>> DEST_COLUMN =
            Options.key("dest_column")
                    .type(new TypeReference<Map<String, Object>>() {})
                    .noDefaultValue()
                    .withDescription("output field default value.");

    public static final Option<List<Map<String, Object>>> COLUMNS =
            Options.key("columns")
                    .type(new TypeReference<List<Map<String, Object>>>() {})
                    .noDefaultValue()
                    .withDescription("columns");

    public static final Option<Boolean> IGNORE_ERROR =
            Options.key("ignore_error")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Ignore error when json path extract error.");

    public static final Option<List<TableTransforms>> MULTI_TABLES =
            Options.key("table_transform")
                    .listType(TableTransforms.class)
                    .noDefaultValue()
                    .withDescription("");

    @Data
    public static class TableTransforms implements Serializable {
        @JsonAlias("table_path")
        private String tablePath;

        @JsonAlias("columns")
        private List<Map<String, Object>> columns;

        @JsonAlias("row_error_handle_way")
        private ErrorHandleWay errorHandleWay;
    }

    private final List<ColumnConfig> columnConfigs;
    @Getter private final ErrorHandleWay errorHandleWay;

    public List<ColumnConfig> getColumnConfigs() {
        return columnConfigs;
    }

    public JsonPathTransformConfig(
            List<ColumnConfig> columnConfigs, ErrorHandleWay errorHandleWay) {
        this.columnConfigs = columnConfigs;
        this.errorHandleWay = errorHandleWay;
    }

    public static JsonPathTransformConfig of(ReadonlyConfig config, CatalogTable table) {
        if (!config.toConfig().hasPath(COLUMNS.key())) {
            throw new TransformException(
                    COLUMNS_MUST_NOT_EMPTY, COLUMNS_MUST_NOT_EMPTY.getErrorMessage());
        }
        ErrorHandleWay rowErrorHandleWay = config.get(CommonOptions.ROW_ERROR_HANDLE_WAY_OPTION);
        List<Map<String, Object>> columns = config.get(COLUMNS);
        List<ColumnConfig> configs = new ArrayList<>(columns.size());
        for (Map<String, Object> map : columns) {
            ReadonlyConfig subConfig = ReadonlyConfig.fromMap(map);
            checkColumnConfig(subConfig);
            String path = subConfig.get(PATH);
            String srcField = subConfig.get(SRC_FIELD);
            String destField = subConfig.get(DEST_FIELD);
            String type = subConfig.get(DEST_TYPE);
            boolean deleteSrcField = subConfig.get(DELETE_SRC_FIELD);
            ErrorHandleWay columnErrorHandleWay =
                    subConfig.get(CommonOptions.COLUMN_ERROR_HANDLE_WAY_OPTION);

            SeaTunnelDataType<?> dataType =
                    SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(srcField, type);
            if (!table.getTableSchema().contains(srcField)) {
                throw TransformCommonError.cannotFindInputFieldError("JsonPath", srcField);
            }
            Column srcFieldColumn = table.getTableSchema().getColumn(srcField);
            Column destFieldColumn =
                    subConfig.getOptional(DEST_COLUMN).isPresent()
                            ? ReadonlyConfigParser.parsePhysicalColumn(
                                    ReadonlyConfig.fromMap(subConfig.get(DEST_COLUMN)))
                            : PhysicalColumn.of(
                                    destField,
                                    dataType,
                                    srcFieldColumn.getColumnLength(),
                                    true,
                                    null,
                                    null);
            ColumnConfig columnConfig =
                    new ColumnConfig(
                            path,
                            srcField,
                            deleteSrcField,
                            destField,
                            destFieldColumn,
                            columnErrorHandleWay);
            configs.add(columnConfig);
        }
        return new JsonPathTransformConfig(configs, rowErrorHandleWay);
    }

    private static void checkColumnConfig(ReadonlyConfig config) {
        String path = config.get(PATH);
        if (StringUtils.isBlank(path)) {
            throw new TransformException(
                    PATH_MUST_NOT_EMPTY, PATH_MUST_NOT_EMPTY.getErrorMessage());
        }
        String srcField = config.get(SRC_FIELD);
        if (StringUtils.isBlank(srcField)) {
            throw new TransformException(
                    SRC_FIELD_MUST_NOT_EMPTY, SRC_FIELD_MUST_NOT_EMPTY.getErrorMessage());
        }
        String destField = config.get(DEST_FIELD);
        Map<String, Object> destColumn = config.get(DEST_COLUMN);
        if (StringUtils.isBlank(destField) && destColumn.isEmpty()) {
            throw new TransformException(
                    DEST_FIELD_MUST_NOT_EMPTY, DEST_FIELD_MUST_NOT_EMPTY.getErrorMessage());
        }
    }

    public static Optional<JsonPathTransformConfig> ofOptional(
            ReadonlyConfig config, CatalogTable catalogTable) {
        String tablePath = catalogTable.getTableId().toTablePath().getFullName();
        if (null != config.get(MULTI_TABLES)) {
            return config.get(MULTI_TABLES).stream()
                    .filter(tableTransforms -> tableTransforms.getTablePath().equals(tablePath))
                    .map(
                            tableTransforms ->
                                    of(
                                            ReadonlyConfig.fromMap(
                                                    new HashMap<String, Object>() {
                                                        {
                                                            put(
                                                                    "columns",
                                                                    tableTransforms.getColumns());
                                                            put(
                                                                    CommonOptions
                                                                            .ROW_ERROR_HANDLE_WAY_OPTION
                                                                            .key(),
                                                                    tableTransforms
                                                                                            .getErrorHandleWay()
                                                                                    != null
                                                                            ? tableTransforms
                                                                                    .getErrorHandleWay()
                                                                                    .toString()
                                                                            : null);
                                                        }
                                                    }),
                                            catalogTable))
                    .findFirst();
        }
        return Optional.of(of(config, catalogTable));
    }
}
