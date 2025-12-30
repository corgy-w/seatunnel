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

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.transform.exception.TransformCommonError;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DML Event Filter Transform Configuration
 *
 * <p>Supports both legacy mode (include_kinds/exclude_kinds only) and new mode (processing_mode
 * with four modes).
 *
 * <p>Legacy mode (backward compatible): - Only include_kinds or exclude_kinds is specified - No
 * processing_mode field - Simple filtering logic
 *
 * <p>New mode: - processing_mode is specified - Supports four modes: FILTER_DML, SOFT_DELETE,
 * APPEND_MODE, ADD_DML_MARKER - Schema enhancement supported
 */
@Slf4j
@Getter
@Setter
@ToString
public class DMLEventFilterTransformConfig implements Serializable {

    private static final long serialVersionUID = -4038443045325795157L;

    /*
     * Legacy fields (backward compatible)
     */
    public static final Option<List<RowKind>> INCLUDE_KINDS =
            Options.key("include_kinds")
                    .listType(RowKind.class)
                    .noDefaultValue()
                    .withDescription("the row kinds to include (legacy mode)");

    public static final Option<List<RowKind>> EXCLUDE_KINDS =
            Options.key("exclude_kinds")
                    .listType(RowKind.class)
                    .noDefaultValue()
                    .withDescription("the row kinds to exclude (legacy mode or FILTER_DML mode)");

    /*
     * New fields (processing_mode and related)
     */
    public static final Option<String> PROCESSING_MODE =
            Options.key("processing_mode")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Processing mode: FILTER_DML, SOFT_DELETE, APPEND_MODE, ADD_DML_MARKER");

    public static final Option<String> MARKER_FIELD_NAME =
            Options.key("marker_field_name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Marker field name (for SOFT_DELETE mode)");

    public static final Option<String> MARKER_FIELD_VALUE =
            Options.key("marker_field_value")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Marker field value (for SOFT_DELETE mode)");

    public static final Option<String> TIMESTAMP_FIELD_NAME =
            Options.key("timestamp_field_name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Timestamp field name");

    public static final Option<String> DML_MARKER_FIELD_NAME =
            Options.key("dml_marker_field_name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("DML marker field name");

    public static final Option<Boolean> TIMESTAMP_IS_PRIMARY_KEY =
            Options.key("timestamp_is_primary_key")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription(
                            "Whether timestamp field participates in primary key in APPEND_MODE");

    public static final Option<Boolean> DML_MARKER_IS_PRIMARY_KEY =
            Options.key("dml_marker_is_primary_key")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether DML marker field participates in primary key in APPEND_MODE");

    public static final Option<String> INSERT_MARKER_VALUE =
            Options.key("insert_marker_value")
                    .stringType()
                    .defaultValue("I")
                    .withDescription("INSERT marker value");

    public static final Option<String> READ_MARKER_VALUE =
            Options.key("read_marker_value")
                    .stringType()
                    .defaultValue("I")
                    .withDescription("READ (snapshot) marker value");

    public static final Option<String> UPDATE_MARKER_VALUE =
            Options.key("update_marker_value")
                    .stringType()
                    .defaultValue("U")
                    .withDescription("UPDATE marker value");

    public static final Option<String> DELETE_MARKER_VALUE =
            Options.key("delete_marker_value")
                    .stringType()
                    .defaultValue("D")
                    .withDescription("DELETE marker value");

    public static final Option<Boolean> SPLIT_UPDATE =
            Options.key("split_update")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to split UPDATE into UPDATE_BEFORE and UPDATE_AFTER");

    public static final Option<String> UPDATE_BEFORE_MARKER_VALUE =
            Options.key("update_before_marker")
                    .stringType()
                    .defaultValue("U")
                    .withDescription("UPDATE_BEFORE marker value");

    public static final Option<String> UPDATE_AFTER_MARKER_VALUE =
            Options.key("update_after_marker")
                    .stringType()
                    .defaultValue("U")
                    .withDescription("UPDATE_AFTER marker value");

    public static final Option<Boolean> DML_MARKER_ENABLED =
            Options.key("dml_marker_enabled")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Whether DML marker is enabled");

    public static final Option<Boolean> TIMESTAMP_ENABLED =
            Options.key("timestamp_enabled")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether timestamp is enabled");

    public static final Option<Integer> TIMESTAMP_PRECISION =
            Options.key("timestamp_precision")
                    .intType()
                    .defaultValue(6)
                    .withDescription(
                            "Precision for timestamp columns (0-9), default is 6 for microsecond precision");

    public static final Option<Integer> MARKER_FIELD_LENGTH =
            Options.key("marker_field_length")
                    .intType()
                    .defaultValue(50)
                    .withDescription("Length for marker string columns, default is 50 characters");

    public static final Option<String> CDC_JSON_FORMAT =
            Options.key("cdc_json_format")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Explicit CDC JSON format for value field enhancement. Supported values: "
                                    + "DEBEZIUM_JSON, COMPATIBLE_DEBEZIUM_JSON, CANAL_JSON, OGG_JSON, KINGBASE_JSON, CUSTOM_JSON, CUSTOM_CDC_JSON.");

    public static final Option<List<String>> CDC_VALUE_FIELD_NAMES =
            Options.key("cdc_value_field_names")
                    .listType(String.class)
                    .defaultValue(Collections.singletonList("value"))
                    .withDescription(
                            "Candidate column names that may contain CDC JSON payload (case insensitive).");

    public static final Option<String> CUSTOM_CDC_OPERATION_FIELD =
            Options.key("custom_cdc_operation_type_field")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Operation type field name for custom CDC JSON format.");

    public static final Option<String> CUSTOM_CDC_DATA_FIELD =
            Options.key("custom_cdc_data_field")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Data field name for custom CDC JSON format.");

    public static final Option<Map<String, String>> CUSTOM_CDC_OPERATION_MAPPING =
            Options.key("custom_cdc_operation_type_mapping")
                    .mapType()
                    .noDefaultValue()
                    .withDescription(
                            "Mapping from custom CDC operation type value to SeaTunnel RowKind, e.g. {\"INSERT\":\"INSERT\"}.");

    public static final Option<Map<String, String>> CUSTOM_CDC_ROW_KIND_MAPPING =
            Options.key("custom_cdc_row_kind_mapping")
                    .mapType()
                    .noDefaultValue()
                    .withDescription(
                            "Mapping from SeaTunnel RowKind to custom CDC operation string. If absent, reverse mapping of custom_cdc_operation_type_mapping will be used.");

    public static final OptionRule OPTION_RULE =
            OptionRule.builder()
                    // Legacy / common options
                    .optional(INCLUDE_KINDS)
                    .optional(EXCLUDE_KINDS)
                    .optional(PROCESSING_MODE)
                    // CDC JSON related options
                    .optional(CDC_JSON_FORMAT)
                    .optional(CDC_VALUE_FIELD_NAMES)
                    .optional(CUSTOM_CDC_OPERATION_FIELD)
                    .optional(CUSTOM_CDC_DATA_FIELD)
                    .optional(CUSTOM_CDC_OPERATION_MAPPING)
                    .optional(CUSTOM_CDC_ROW_KIND_MAPPING)
                    // SOFT_DELETE / APPEND_MODE / ADD_DML_MARKER shared options
                    .optional(TIMESTAMP_IS_PRIMARY_KEY)
                    .optional(DML_MARKER_IS_PRIMARY_KEY)
                    .optional(INSERT_MARKER_VALUE)
                    .optional(READ_MARKER_VALUE)
                    .optional(UPDATE_MARKER_VALUE)
                    .optional(DELETE_MARKER_VALUE)
                    .optional(SPLIT_UPDATE)
                    .optional(UPDATE_BEFORE_MARKER_VALUE)
                    .optional(UPDATE_AFTER_MARKER_VALUE)
                    .optional(DML_MARKER_ENABLED)
                    .optional(TIMESTAMP_ENABLED)
                    .optional(TIMESTAMP_PRECISION)
                    .optional(MARKER_FIELD_LENGTH)
                    .build();

    /*
     * Legacy fields
     */
    private Set<RowKind> includeKinds = Collections.emptySet();
    private Set<RowKind> excludeKinds = Collections.emptySet();

    /*
     * New fields
     */
    private ProcessingMode processingMode;

    private String markerFieldName;
    private String markerFieldValue;

    private String timestampFieldName;
    private String dmlMarkerFieldName;
    private String insertMarkerValue;
    private String readMarkerValue;
    private String updateMarkerValue;
    private String deleteMarkerValue;
    private Boolean timestampIsPrimaryKey = true;
    private Boolean dmlMarkerIsPrimaryKey = false;
    private Boolean splitUpdate;
    private String updateBeforeMarkerValue;
    private String updateAfterMarkerValue;

    private Boolean dmlMarkerEnabled;
    private Boolean timestampEnabled;

    private Integer timestampPrecision = TIMESTAMP_PRECISION.defaultValue();
    private Integer markerFieldLength = MARKER_FIELD_LENGTH.defaultValue();
    private List<String> cdcValueFieldNames = new ArrayList<>(Collections.singletonList("value"));
    private String cdcJsonFormat;
    private String customCdcOperationTypeField;
    private String customCdcDataField;
    private Map<String, RowKind> customCdcOperationTypeMapping = Collections.emptyMap();
    private Map<RowKind, String> customCdcRowKindMapping = Collections.emptyMap();

    public static DMLEventFilterTransformConfig of(ReadonlyConfig config) {
        DMLEventFilterTransformConfig transformConfig = new DMLEventFilterTransformConfig();

        // Parse legacy fields
        if (config.get(INCLUDE_KINDS) != null) {
            transformConfig.setIncludeKinds(new HashSet<>(config.get(INCLUDE_KINDS)));
        }
        if (config.get(EXCLUDE_KINDS) != null) {
            transformConfig.setExcludeKinds(new HashSet<>(config.get(EXCLUDE_KINDS)));
        }

        // Always parse basic scalar fields so that legacy mode also has sane defaults.
        // NOTE: ReadonlyConfig.get(...) will return the option default value when the user
        // doesn't configure it explicitly.
        transformConfig.setTimestampPrecision(config.get(TIMESTAMP_PRECISION));
        transformConfig.setMarkerFieldLength(config.get(MARKER_FIELD_LENGTH));

        // Parse new fields (processing_mode)
        ProcessingMode processingMode = null;
        if (config.get(PROCESSING_MODE) != null) {
            String modeStr = config.get(PROCESSING_MODE);
            processingMode = ProcessingMode.valueOf(modeStr);
            transformConfig.setProcessingMode(processingMode);

            // Parse mode-specific fields
            if (config.get(MARKER_FIELD_NAME) != null) {
                transformConfig.setMarkerFieldName(config.get(MARKER_FIELD_NAME));
            }
            if (config.get(MARKER_FIELD_VALUE) != null) {
                transformConfig.setMarkerFieldValue(config.get(MARKER_FIELD_VALUE));
            }

            if (config.get(TIMESTAMP_FIELD_NAME) != null) {
                transformConfig.setTimestampFieldName(config.get(TIMESTAMP_FIELD_NAME));
            }
            if (config.get(DML_MARKER_FIELD_NAME) != null) {
                transformConfig.setDmlMarkerFieldName(config.get(DML_MARKER_FIELD_NAME));
            }

            transformConfig.setTimestampIsPrimaryKey(config.get(TIMESTAMP_IS_PRIMARY_KEY));
            transformConfig.setDmlMarkerIsPrimaryKey(config.get(DML_MARKER_IS_PRIMARY_KEY));

            String insertMarker = config.get(INSERT_MARKER_VALUE);
            transformConfig.setInsertMarkerValue(insertMarker);
            String readMarker = config.getOptional(READ_MARKER_VALUE).orElse(insertMarker);
            transformConfig.setReadMarkerValue(readMarker);
            transformConfig.setUpdateMarkerValue(config.get(UPDATE_MARKER_VALUE));
            transformConfig.setDeleteMarkerValue(config.get(DELETE_MARKER_VALUE));
            transformConfig.setSplitUpdate(config.get(SPLIT_UPDATE));
            transformConfig.setUpdateBeforeMarkerValue(config.get(UPDATE_BEFORE_MARKER_VALUE));
            transformConfig.setUpdateAfterMarkerValue(config.get(UPDATE_AFTER_MARKER_VALUE));

            transformConfig.setDmlMarkerEnabled(config.get(DML_MARKER_ENABLED));

            // Set timestamp_enabled with mode-dependent default
            // APPEND_MODE should default to true, other modes should default to false
            boolean timestampEnabled;
            if (config.getOptional(TIMESTAMP_ENABLED).isPresent()) {
                // User explicitly configured it
                timestampEnabled = config.get(TIMESTAMP_ENABLED);
            } else {
                // Use mode-dependent default
                timestampEnabled =
                        (processingMode != null && processingMode == ProcessingMode.APPEND_MODE);
            }
            transformConfig.setTimestampEnabled(timestampEnabled);
        }

        if (config.get(CDC_JSON_FORMAT) != null) {
            transformConfig.setCdcJsonFormat(config.get(CDC_JSON_FORMAT));
        }

        if (config.get(CDC_VALUE_FIELD_NAMES) != null) {
            transformConfig.setCdcValueFieldNames(
                    new ArrayList<>(config.get(CDC_VALUE_FIELD_NAMES)));
        }

        if (config.get(CUSTOM_CDC_OPERATION_FIELD) != null) {
            transformConfig.setCustomCdcOperationTypeField(config.get(CUSTOM_CDC_OPERATION_FIELD));
        }

        if (config.get(CUSTOM_CDC_DATA_FIELD) != null) {
            transformConfig.setCustomCdcDataField(config.get(CUSTOM_CDC_DATA_FIELD));
        }

        if (config.get(CUSTOM_CDC_OPERATION_MAPPING) != null) {
            Map<String, RowKind> mapping = new HashMap<>();
            config.get(CUSTOM_CDC_OPERATION_MAPPING)
                    .forEach(
                            (key, value) -> {
                                try {
                                    mapping.put(key, RowKind.valueOf(value));
                                } catch (IllegalArgumentException e) {
                                    throw TransformCommonError.configValidationFailed(
                                            DMLEventFilterTransform.PLUGIN_NAME,
                                            String.format(
                                                    "Unsupported RowKind '%s' in custom_cdc_operation_type_mapping",
                                                    value));
                                }
                            });
            transformConfig.setCustomCdcOperationTypeMapping(mapping);
        }

        if (config.get(CUSTOM_CDC_ROW_KIND_MAPPING) != null) {
            Map<RowKind, String> reverse = new HashMap<>();
            config.get(CUSTOM_CDC_ROW_KIND_MAPPING)
                    .forEach(
                            (key, value) -> {
                                try {
                                    reverse.put(RowKind.valueOf(key), value);
                                } catch (IllegalArgumentException e) {
                                    throw TransformCommonError.configValidationFailed(
                                            DMLEventFilterTransform.PLUGIN_NAME,
                                            String.format(
                                                    "Unsupported RowKind key '%s' in custom_cdc_row_kind_mapping",
                                                    key));
                                }
                            });
            transformConfig.setCustomCdcRowKindMapping(reverse);
        }

        // CRITICAL: Validate config AFTER all fields are set (not before)
        // This ensures cdc_json_format, cdc_value_field_names, and custom_cdc_* validations work
        // correctly
        if (config.get(PROCESSING_MODE) != null) {
            validateConfig(transformConfig);
        }

        return transformConfig;
    }

    public static DMLEventFilterTransformConfig buildConfig(Map<String, Object> rawConfig) {
        return of(ReadonlyConfig.fromMap(rawConfig));
    }

    private static void validateConfig(DMLEventFilterTransformConfig config) {
        if (config.getCdcValueFieldNames() == null || config.getCdcValueFieldNames().isEmpty()) {
            throw TransformCommonError.configValidationFailed(
                    DMLEventFilterTransform.PLUGIN_NAME,
                    "cdc_value_field_names must contain at least one candidate column name");
        }

        // Validate timestamp precision
        if (config.getTimestampPrecision() < 0 || config.getTimestampPrecision() > 9) {
            throw TransformCommonError.configValidationFailed(
                    DMLEventFilterTransform.PLUGIN_NAME,
                    "timestamp_precision must be between 0 and 9, but got: "
                            + config.getTimestampPrecision());
        }

        // Validate marker field length
        if (config.getMarkerFieldLength() <= 0) {
            throw TransformCommonError.configValidationFailed(
                    DMLEventFilterTransform.PLUGIN_NAME,
                    "marker_field_length must be greater than 0, but got: "
                            + config.getMarkerFieldLength());
        }

        // Validate explicit CDC JSON format when configured
        if (config.getCdcJsonFormat() != null && !config.getCdcJsonFormat().trim().isEmpty()) {
            String format = config.getCdcJsonFormat().trim().toUpperCase();
            switch (format) {
                case "DEBEZIUM_JSON":
                case "COMPATIBLE_DEBEZIUM_JSON":
                case "CANAL_JSON":
                case "OGG_JSON":
                case "KINGBASE_JSON":
                case "CUSTOM_JSON":
                case "CUSTOM_CDC_JSON":
                    break;
                default:
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "Unsupported cdc_json_format: "
                                    + config.getCdcJsonFormat()
                                    + ". Supported values: DEBEZIUM_JSON, COMPATIBLE_DEBEZIUM_JSON, CANAL_JSON, OGG_JSON, KINGBASE_JSON, CUSTOM_JSON, CUSTOM_CDC_JSON");
            }
        }

        ProcessingMode mode = config.getProcessingMode();

        switch (mode) {
            case FILTER_DML:
                break;

            case SOFT_DELETE:
                if (config.getMarkerFieldName() == null
                        || config.getMarkerFieldName().trim().isEmpty()) {
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "SOFT_DELETE mode requires 'marker_field_name' to be specified");
                }
                if (config.getMarkerFieldValue() == null
                        || config.getMarkerFieldValue().trim().isEmpty()) {
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "SOFT_DELETE mode requires 'marker_field_value' to be specified");
                }
                break;

            case APPEND_MODE:
                if (config.getTimestampFieldName() == null
                        || config.getTimestampFieldName().trim().isEmpty()) {
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "APPEND_MODE mode requires 'timestamp_field_name' to be specified");
                }
                if (config.getDmlMarkerFieldName() == null
                        || config.getDmlMarkerFieldName().trim().isEmpty()) {
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "APPEND_MODE mode requires 'dml_marker_field_name' to be specified");
                }
                break;

            case ADD_DML_MARKER:
                if (config.getDmlMarkerEnabled()
                        && (config.getDmlMarkerFieldName() == null
                                || config.getDmlMarkerFieldName().trim().isEmpty())) {
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "ADD_DML_MARKER mode with dml_marker_enabled=true requires 'dml_marker_field_name' to be specified");
                }
                if (config.getTimestampEnabled()
                        && (config.getTimestampFieldName() == null
                                || config.getTimestampFieldName().trim().isEmpty())) {
                    throw TransformCommonError.configValidationFailed(
                            DMLEventFilterTransform.PLUGIN_NAME,
                            "ADD_DML_MARKER mode with timestamp_enabled=true requires 'timestamp_field_name' to be specified");
                }
                break;
        }
    }
}
