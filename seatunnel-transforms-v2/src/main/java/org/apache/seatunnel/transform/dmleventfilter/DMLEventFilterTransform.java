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

import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelFlatMapTransform;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.transform.common.AbstractCatalogSupportFlatMapTransform;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.CanalJsonEnhancer;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.CdcJsonEnhanceException;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.CdcJsonEnhancerManager;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.CompatibleDebeziumJsonEnhancer;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.CustomCdcConfig;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.DebeziumJsonEnhancer;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.ICdcJsonEnhancer;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.IUpdateSplittableEnhancer;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.KingbaseJsonEnhancer;
import org.apache.seatunnel.transform.dmleventfilter.jsonenhancer.OggJsonEnhancer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DML Event Filter Transform
 *
 * <p>This transform processes multiple CDC tables simultaneously and supports both legacy mode and
 * new mode:
 *
 * <p><b>Legacy mode (backward compatible):</b>
 *
 * <ul>
 *   <li>Only include_kinds or exclude_kinds is specified
 *   <li>No processing_mode field
 *   <li>Simple filtering logic based on RowKind
 * </ul>
 *
 * <p><b>New mode:</b>
 *
 * <ul>
 *   <li>processing_mode is specified
 *   <li>Supports four modes: FILTER_DML, SOFT_DELETE, APPEND_MODE, ADD_DML_MARKER
 *   <li>Supports both CDC JSON formats (Debezium, Canal, OGG, Kingbase, Custom) and flat data
 *   <li>In APPEND_MODE with split_update=true, UPDATE events can be split into BEFORE and AFTER
 *       records
 * </ul>
 *
 * <p>This transform now implements {@link SeaTunnelFlatMapTransform} to support one-to-many
 * transformations, particularly for splitting UPDATE events in Debezium CDC scenarios.
 */
@Slf4j
public class DMLEventFilterTransform extends AbstractCatalogSupportFlatMapTransform {

    private static final long serialVersionUID = 6243287982709134877L;

    public static String PLUGIN_NAME = "DMLEventFilter";

    /** Configuration for the transform shared across all tables */
    private final DMLEventFilterTransformConfig config;

    /**
     * Per-table processor map for multi-table routing Key: table ID in format:
     * "database.schema.table" Value: TableProcessor instance
     */
    private final Map<String, TableProcessor> processorMap;

    public DMLEventFilterTransform(
            @NonNull List<CatalogTable> inputCatalogTables, @NonNull ReadonlyConfig config) {
        super(inputCatalogTables, config);
        this.config = DMLEventFilterTransformConfig.of(config);
        this.processorMap = new HashMap<>();

        // Create one processor per table for isolated processing state
        for (CatalogTable table : inputCatalogTables) {
            String tableId = table.getTableId().toTablePath().toString();
            processorMap.put(tableId, new TableProcessor(this.config, table));
        }

        if (this.config.getProcessingMode() != null) {
            log.info(
                    "DMLEventFilterTransform initialized with new mode: {} for {} tables",
                    this.config.getProcessingMode(),
                    inputCatalogTables.size());
        } else {
            // Legacy mode: simple filtering
            log.info(
                    "DMLEventFilterTransform initialized with legacy mode for {} tables",
                    inputCatalogTables.size());
        }

        // CRITICAL: split_update requires flatMap-aware execution environment
        if (this.config.getSplitUpdate() != null && this.config.getSplitUpdate()) {
            log.warn(
                    "⚠️  IMPORTANT: split_update is enabled. This transform implements SeaTunnelFlatMapTransform "
                            + "to output multiple records (BEFORE/AFTER) per UPDATE event. "
                            + "Ensure your execution environment supports flatMap() method (e.g., SeaTunnel Engine). "
                            + "If the runner only calls map(), only the first output record will be returned, causing data loss. "
                            + "Verified flatMap-aware runners: SeaTunnel Engine (>=3.0).");
        }
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    /**
     * Build transform for a single table (required by AbstractMultiCatalogSupportTransform).
     *
     * <p>Returns empty because we handle all tables directly in constructor via processorMap. The
     * framework calls map() method which routes to the appropriate TableProcessor.
     */
    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return Optional.empty();
    }

    /**
     * Transform a row by routing to the appropriate table processor (legacy map method for backward
     * compatibility).
     *
     * <p>Note: This method is kept for backward compatibility with code that directly calls map().
     * The flatMap() method is the primary entry point for multi-output transformations.
     *
     * <p>IMPORTANT: If split_update is enabled and the transform produces multiple output records,
     * this method will fail-fast to prevent silent data loss. Use a flatMap-aware execution
     * environment (e.g., SeaTunnel Engine) instead.
     */
    @Override
    public SeaTunnelRow map(SeaTunnelRow row) {
        List<SeaTunnelRow> results = flatMap(row);
        if (results == null || results.isEmpty()) {
            return null;
        }

        // CRITICAL: Fail-fast if multi-output is produced but runner only supports single output
        // This prevents silent data loss when split_update is enabled on non-flatMap-aware runners
        if (results.size() > 1) {
            throw new UnsupportedOperationException(
                    String.format(
                            "This transform produced %d output records (likely due to split_update=true), "
                                    + "but the execution environment only called map() which returns a single record. "
                                    + "This would cause data loss of %d records. "
                                    + "Please use a flatMap-aware execution environment (e.g., SeaTunnel Engine >= 3.0) "
                                    + "that properly expands multi-output transforms. "
                                    + "Table: %s",
                            results.size(), results.size() - 1, row.getTableId()));
        }

        return results.get(0);
    }

    /**
     * Transform a row by routing to the appropriate table processor, supporting multi-output.
     *
     * <p>This method enables one-to-many transformations:
     *
     * <ul>
     *   <li>Normal cases: returns a single-element list
     *   <li>UPDATE splitting (APPEND_MODE + split_update=true): returns two elements (BEFORE and
     *       AFTER)
     *   <li>Filtering: returns empty list
     * </ul>
     *
     * @param row Input row from CDC source
     * @return List of transformed rows (can be empty, single, or multiple records)
     */
    @Override
    public List<SeaTunnelRow> flatMap(SeaTunnelRow row) {
        try {
            if (processorMap.size() == 1) {
                // Single table optimization: skip HashMap lookup
                return processorMap.values().iterator().next().process(row);
            }
            // Multi-table routing: O(1) HashMap lookup by table ID
            TableProcessor processor = processorMap.get(row.getTableId());
            if (processor == null) {
                throw new IllegalStateException(
                        String.format(
                                "No processor found for table: %s. Available tables: %s",
                                row.getTableId(), processorMap.keySet()));
            }
            return processor.process(row);
        } catch (org.apache.seatunnel.transform.exception.ErrorDataTransformException e) {
            if (e.getErrorHandleWay() != null) {
                org.apache.seatunnel.transform.common.ErrorHandleWay errorHandleWay =
                        e.getErrorHandleWay();
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

    /**
     * Get all produced catalog tables with potentially modified schemas. Each table may have
     * different output schema based on: 1.Processing mode (SOFT_DELETE adds marker column,
     * APPEND_MODE adds timestamp+DML marker, etc.) 2.CDC JSON detection (value field presence
     * affects schema transformation)
     */
    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        List<CatalogTable> result = new ArrayList<>(inputCatalogTables.size());
        for (CatalogTable inputTable : inputCatalogTables) {
            String tableId = inputTable.getTableId().toTablePath().toString();
            result.add(processorMap.get(tableId).getOutputCatalogTable());
        }
        return result;
    }

    @Override
    public CatalogTable getProducedCatalogTable() {
        return getProducedCatalogTables().get(0);
    }

    public SeaTunnelDataType<SeaTunnelRow> getProducedType() {
        return getProducedCatalogTable().getTableSchema().toPhysicalRowDataType();
    }

    /**
     * TableProcessor - Internal POJO for single table processing logic This is a lightweight helper
     * class that encapsulates all business logic for processing a single table. Each table gets its
     * own processor instance
     */
    private static class TableProcessor implements Serializable {

        private static final long serialVersionUID = 1L;

        private final DMLEventFilterTransformConfig config;

        /** Input catalog table for this processor */
        private final CatalogTable inputCatalogTable;

        /** Output catalog table with potentially modified schema */
        private final CatalogTable outputCatalogTable;

        /** CDC JSON enhancer manager for detecting and enhancing CDC formats */
        private final CdcJsonEnhancerManager enhancerManager;

        /** Legacy mode: RowKinds to include (empty if using exclude_kinds or new mode) */
        private final Set<RowKind> includeKinds;

        /** Legacy/new mode: RowKinds to exclude (empty if using include_kinds) */
        private final Set<RowKind> excludeKinds;

        /** Candidate field names for CDC JSON value field (default: ["value"]) */
        private final List<String> cdcValueFieldNames;

        /**
         * Cached timestamp formatter based on configured precision
         *
         * <p>Marked as transient to avoid serialization issues with Hazelcast. DateTimeFormatter is
         * not Serializable, so we use lazy initialization.
         */
        private transient java.time.format.DateTimeFormatter timestampFormatter;

        /** Field index for soft delete marker column (-1 if not applicable) */
        private int markerFieldIndex = -1;

        /** Field index for append mode timestamp column (-1 if not applicable) */
        private int timestampFieldIndex = -1;

        /** Field index for DML marker column (-1 if not applicable) */
        private int dmlMarkerFieldIndex = -1;

        /**
         * Field index for CDC JSON value field (-1 if flat data)
         *
         * <p>If >= 0, this table uses CDC JSON format (Debezium, Canal, OGG, etc.) and fields are
         * added inside the JSON value instead of to external schema.
         */
        private int valueFieldIndex = -1;

        /**
         * Cached CDC enhancer for this table's detected format
         *
         * <p>Lazy initialization and caching. First CDC row detection populates this cache,
         * subsequent rows reuse it for performance.
         */
        private ICdcJsonEnhancer cachedEnhancer = null;

        /** Whether CDC format/enhancer has been determined for this table. */
        private boolean enhancerLocked = false;

        /**
         * Initialize TableProcessor for a single input catalog table.
         *
         * <p>Initialization steps:
         *
         * <ol>
         *   <li>Parse CDC value field names from config (default: ["value"])
         *   <li>Build custom CDC config and register enhancer
         *   <li>Copy include/exclude RowKind sets
         *   <li>Detect CDC value field index by scanning column names
         *   <li>Build output catalog table with potentially modified schema
         *   <li>Initialize field indexes for added columns
         * </ol>
         */
        TableProcessor(DMLEventFilterTransformConfig config, CatalogTable inputCatalogTable) {
            this.config = config;
            this.inputCatalogTable = inputCatalogTable;

            // Default CDC value field name is "value" (Debezium/Canal/OGG standard)
            this.cdcValueFieldNames =
                    config.getCdcValueFieldNames() == null
                            ? Collections.singletonList("value")
                            : new ArrayList<>(config.getCdcValueFieldNames());

            // Build and register custom CDC format enhancer if configured
            CustomCdcConfig customCdcConfig = buildCustomCdcConfig(config);
            this.enhancerManager = CdcJsonEnhancerManager.create();
            this.enhancerManager.registerCustomEnhancer(customCdcConfig);

            this.includeKinds = config.getIncludeKinds();
            this.excludeKinds = config.getExcludeKinds();

            // Note: timestampFormatter uses lazy initialization (transient field)
            // to avoid serialization issues with Hazelcast

            // Detect CDC JSON value field index (sets valueFieldIndex)
            initializeValueFieldIndex();

            // Initialize CDC JSON enhancer from explicit configuration if provided
            initializeEnhancerFromConfig();

            // Build output schema with potentially added columns
            this.outputCatalogTable = buildOutputCatalogTable();

            // Initialize field indexes for added columns (marker, timestamp, dml_marker)
            initializeFieldIndexes();
        }

        /**
         * Process a single row for this table, returning a list of output rows.
         *
         * <p>This is the primary entry point for flatMap-based processing:
         *
         * <ul>
         *   <li>Legacy mode: returns single-element list or empty list for filtering
         *   <li>New mode APPEND_MODE with split_update=true: may return two-element list (BEFORE +
         *       AFTER)
         *   <li>Other modes: returns single-element list or empty list
         * </ul>
         *
         * @param inputRow Input row from CDC source
         * @return List of transformed rows (empty, single, or multiple records)
         */
        List<SeaTunnelRow> process(SeaTunnelRow inputRow) {
            // Check if we need to split UPDATE events in APPEND_MODE
            if (config.getProcessingMode() == ProcessingMode.APPEND_MODE
                    && config.getSplitUpdate()
                    && valueFieldIndex >= 0) {
                // Debezium JSON path: try to split UPDATE
                return processWithUpdateSplitting(inputRow);
            }

            // Default path: use existing transform() method
            SeaTunnelRow result = transform(inputRow);
            if (result == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(result);
        }

        /**
         * Process row with potential UPDATE splitting for Debezium JSON in APPEND_MODE.
         *
         * <p>When split_update=true and op="u", splits UPDATE into BEFORE and AFTER records:
         *
         * <ul>
         *   <li>BEFORE: payload.before data -> payload.after, op="c", marker=BEFORE
         *   <li>AFTER: payload.after data stays, op="c", marker=AFTER
         * </ul>
         *
         * @param inputRow Input row from CDC source
         * @return List containing one or two records
         */
        private List<SeaTunnelRow> processWithUpdateSplitting(SeaTunnelRow inputRow) {
            RowKind rowKind = inputRow.getRowKind();
            Object valueField = inputRow.getField(valueFieldIndex);

            if (valueField == null) {
                // No value field, fallback to normal processing
                SeaTunnelRow result = transform(inputRow);
                return result == null ? Collections.emptyList() : Collections.singletonList(result);
            }

            JsonNode valueNode = parseValueFieldToJsonNode(valueField);
            if (valueNode == null) {
                // Failed to parse JSON, fallback to normal processing
                SeaTunnelRow result = transform(inputRow);
                return result == null ? Collections.emptyList() : Collections.singletonList(result);
            }

            ICdcJsonEnhancer enhancer = detectEnhancerWithCache(valueNode);
            if (enhancer == null) {
                // No enhancer, fallback to normal processing
                SeaTunnelRow result = transform(inputRow);
                return result == null ? Collections.emptyList() : Collections.singletonList(result);
            }

            boolean supportsUpdateSplitting = enhancer instanceof IUpdateSplittableEnhancer;

            // Normalize RowKind when source always emits INSERT with op in JSON
            if (rowKind == RowKind.INSERT) {
                try {
                    RowKind parsedKind = enhancer.parseRowKind(valueNode);
                    if (parsedKind != null) {
                        rowKind = parsedKind;
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse RowKind from CDC JSON value field", e);
                }
            }

            // Check if this is an UPDATE that should be split
            if (rowKind == RowKind.UPDATE_BEFORE || rowKind == RowKind.UPDATE_AFTER) {
                try {
                    if (!supportsUpdateSplitting) {
                        SeaTunnelRow result =
                                appendModeDebeziumJson(
                                        inputRow, rowKind, valueField, valueNode, enhancer);
                        return result == null
                                ? Collections.emptyList()
                                : Collections.singletonList(result);
                    }

                    return splitUpdateDebeziumJson(
                            inputRow,
                            rowKind,
                            valueField,
                            valueNode,
                            (IUpdateSplittableEnhancer) enhancer);
                } catch (Exception e) {
                    log.error("Failed to split UPDATE event, fallback to normal processing", e);
                    SeaTunnelRow result =
                            appendModeDebeziumJson(
                                    inputRow, rowKind, valueField, valueNode, enhancer);
                    return result == null
                            ? Collections.emptyList()
                            : Collections.singletonList(result);
                }
            }

            // Not an UPDATE, use normal append mode processing
            SeaTunnelRow result =
                    appendModeDebeziumJson(inputRow, rowKind, valueField, valueNode, enhancer);
            return result == null ? Collections.emptyList() : Collections.singletonList(result);
        }

        /**
         * Transform a single row for this table (legacy method).
         *
         * <p>Entry point for per-table row transformation:
         *
         * <ul>
         *   <li>Legacy mode: simple include/exclude filtering
         *   <li>New mode: normalize RowKind from CDC JSON, then dispatch to processing mode
         * </ul>
         *
         * @param inputRow Input row from CDC source
         * @return Transformed row, or null if filtered out
         */
        SeaTunnelRow transform(SeaTunnelRow inputRow) {
            if (config.getProcessingMode() == null) {
                // Legacy mode: backward compatible filtering
                return filterDMLLegacy(inputRow);
            }

            // New mode: optionally normalize RowKind from CDC JSON and reuse parsed JSON/enhancer
            RowKind rowKind = inputRow.getRowKind();
            Object valueField = null;
            JsonNode valueNode = null;
            ICdcJsonEnhancer enhancer = null;

            if (valueFieldIndex >= 0) {
                // CDC JSON path: parse once and detect enhancer once
                valueField = inputRow.getField(valueFieldIndex);
                if (valueField != null) {
                    valueNode = parseValueFieldToJsonNode(valueField);
                    if (valueNode != null) {
                        enhancer = detectEnhancerWithCache(valueNode);
                    }
                }

                // Normalize RowKind when source always emits INSERT with op in JSON
                if (rowKind == RowKind.INSERT && enhancer != null && valueNode != null) {
                    try {
                        RowKind parsedKind = enhancer.parseRowKind(valueNode);
                        if (parsedKind != null) {
                            inputRow.setRowKind(parsedKind);
                            rowKind = parsedKind;
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse RowKind from CDC JSON value field", e);
                    }
                }
            }

            // Dispatch to processing mode handler
            switch (config.getProcessingMode()) {
                case FILTER_DML:
                    return filterDML(inputRow, rowKind);
                case SOFT_DELETE:
                    if (valueFieldIndex >= 0) {
                        return softDeleteCdcJson(
                                inputRow, rowKind, valueField, valueNode, enhancer);
                    } else {
                        return softDeleteFlat(inputRow, rowKind);
                    }
                case APPEND_MODE:
                    if (rowKind == RowKind.UPDATE_BEFORE && !config.getSplitUpdate()) {
                        // Filter out UPDATE_BEFORE when split_update is disabled
                        return null;
                    }
                    if (valueFieldIndex >= 0) {
                        return appendModeDebeziumJson(
                                inputRow, rowKind, valueField, valueNode, enhancer);
                    } else {
                        // Flat data: append timestamp and DML marker columns externally
                        return appendModeFlat(inputRow, rowKind);
                    }
                case ADD_DML_MARKER:
                    if (valueFieldIndex >= 0) {
                        return addDMLMarkerCdcJson(
                                inputRow, rowKind, valueField, valueNode, enhancer);
                    } else {
                        // Flat data: append columns externally
                        return addDMLMarkerFlat(inputRow, rowKind);
                    }
                default:
                    return inputRow;
            }
        }

        /**
         * Get the output catalog table with potentially modified schema.
         *
         * @return Output catalog table built during initialization
         */
        CatalogTable getOutputCatalogTable() {
            return outputCatalogTable;
        }

        /*
         * Legacy mode filtering logic (backward compatible)
         *
         * Only include_kinds or exclude_kinds is specified, no processing_mode.
         * Simple filtering based on RowKind without schema enhancement.
         */
        private SeaTunnelRow filterDMLLegacy(SeaTunnelRow row) {
            if (!this.excludeKinds.isEmpty()) {
                // Exclude mode: filter out excluded RowKinds
                return this.excludeKinds.contains(row.getRowKind()) ? null : row;
            }
            if (!this.includeKinds.isEmpty()) {
                // Include mode: only keep included RowKinds
                return this.includeKinds.contains(row.getRowKind()) ? row : null;
            }
            // No filter configured: pass through
            return row;
        }

        /*
         * FILTER_DML mode: filter rows based on exclude_kinds configuration
         *
         * Similar to legacy mode but works with normalized RowKind from CDC JSON.
         */
        private SeaTunnelRow filterDML(SeaTunnelRow inputRow, RowKind rowKind) {
            Set<RowKind> excludeKinds = config.getExcludeKinds();
            if (!excludeKinds.isEmpty() && excludeKinds.contains(rowKind)) {
                // Filter out this row
                return null;
            }
            return inputRow;
        }

        /*
         * SOFT_DELETE for CDC JSON format: enhance the CDC JSON value field internally
         *
         * DELETE rows are converted to UPDATE_AFTER with marker field set to marker_field_value.
         * INSERT/UPDATE rows get marker field added and set to NULL.
         * External schema (topic, key, value) remains unchanged.
         */
        private SeaTunnelRow softDeleteCdcJson(
                SeaTunnelRow inputRow,
                RowKind rowKind,
                Object valueField,
                JsonNode valueNode,
                ICdcJsonEnhancer enhancer) {
            if (valueField == null) {
                log.warn("CDC value field is null, skip enhancement");
                return inputRow;
            }

            if (valueNode == null) {
                log.warn("Failed to parse CDC JSON, skip enhancement");
                return inputRow;
            }

            if (enhancer == null) {
                log.warn("No CDC format enhancer found, skip enhancement");
                return inputRow;
            }

            try {
                // Build fields to add into CDC JSON value
                java.util.Map<String, Object> fieldsToAdd = new java.util.HashMap<>();
                if (rowKind == RowKind.DELETE) {
                    // DELETE: set marker to marker_field_value
                    fieldsToAdd.put(config.getMarkerFieldName(), config.getMarkerFieldValue());
                } else {
                    // INSERT/UPDATE: set marker to NULL
                    fieldsToAdd.put(config.getMarkerFieldName(), null);
                }

                // DELETE is converted to UPDATE_AFTER for downstream sink
                RowKind targetRowKind =
                        (rowKind == RowKind.DELETE) ? RowKind.UPDATE_AFTER : rowKind;

                // Enhance CDC JSON (adds marker field inside value JSON)
                JsonNode enhancedNode =
                        enhancer.enhance(valueNode, rowKind, targetRowKind, fieldsToAdd);

                // Serialize back to JSON string
                String updatedValueJson = JsonUtils.toJsonString(enhancedNode);

                // Create output row with same field count (value field is replaced)
                Object[] outputFields = new Object[inputRow.getFields().length];
                System.arraycopy(
                        inputRow.getFields(), 0, outputFields, 0, inputRow.getFields().length);
                outputFields[valueFieldIndex] = updatedValueJson;

                SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
                outputRow.setTableId(inputRow.getTableId());
                outputRow.setRowKind(targetRowKind);

                return outputRow;
            } catch (CdcJsonEnhanceException e) {
                log.error("Failed to enhance CDC JSON for SOFT_DELETE", e);
                throw new CdcJsonEnhanceException("Failed to enhance CDC JSON for SOFT_DELETE", e);
            } catch (Exception e) {
                log.error("Failed to enhance CDC JSON for SOFT_DELETE", e);
                throw new CdcJsonEnhanceException("Failed to enhance CDC JSON for SOFT_DELETE", e);
            }
        }

        /*
         * SOFT_DELETE for flat data: append a logical delete marker column externally
         *
         * DELETE rows are converted to UPDATE_AFTER with marker column set to marker_field_value.
         * INSERT/UPDATE rows get marker column appended and set to NULL.
         */
        private SeaTunnelRow softDeleteFlat(SeaTunnelRow inputRow, RowKind rowKind) {
            int inputFieldLength = inputCatalogTable.getTableSchema().getColumns().size();
            int outputFieldLength = inputFieldLength + 1;

            Object[] outputFields = new Object[outputFieldLength];
            System.arraycopy(inputRow.getFields(), 0, outputFields, 0, inputFieldLength);

            if (rowKind == RowKind.DELETE) {
                // DELETE: append marker column with marker_field_value, emit as UPDATE_AFTER
                outputFields[markerFieldIndex] = config.getMarkerFieldValue();
                SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
                outputRow.setTableId(inputRow.getTableId());
                outputRow.setRowKind(RowKind.UPDATE_AFTER);
                return outputRow;
            } else {
                // INSERT/UPDATE: append marker column with NULL, keep original RowKind
                outputFields[markerFieldIndex] = null;
                SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
                outputRow.setTableId(inputRow.getTableId());
                outputRow.setRowKind(inputRow.getRowKind());
                return outputRow;
            }
        }

        /*
         * APPEND_MODE for CDC JSON format: enhance the CDC JSON value internally
         *
         * Adds operation_time (timestamp) and dml_marker_field inside value JSON.
         * All rows are emitted as INSERT regardless of original RowKind.
         * External schema (topic, key, value) remains unchanged.
         */
        private SeaTunnelRow appendModeDebeziumJson(
                SeaTunnelRow inputRow,
                RowKind rowKind,
                Object valueField,
                JsonNode valueNode,
                ICdcJsonEnhancer enhancer) {
            if (valueField == null) {
                log.warn("CDC value field is null, skip enhancement");
                return inputRow;
            }

            if (valueNode == null) {
                log.warn("Failed to parse CDC JSON, skip enhancement");
                return inputRow;
            }

            if (enhancer == null) {
                log.warn(
                        "No CDC format enhancer found for value JSON, skip enhancement: {}",
                        valueNode);
                return inputRow;
            }

            try {
                // Build fields to add into CDC JSON value
                java.util.Map<String, Object> fieldsToAdd = new java.util.HashMap<>();
                fieldsToAdd.put(
                        config.getTimestampFieldName(),
                        LocalDateTime.now().format(getTimestampFormatter()));
                fieldsToAdd.put(config.getDmlMarkerFieldName(), getDMLMarkerValue(rowKind));

                // Enhance CDC JSON (target RowKind: keep READ as READ, others as INSERT)
                RowKind targetRowKind = (rowKind == RowKind.READ) ? RowKind.READ : RowKind.INSERT;
                JsonNode enhancedNode =
                        enhancer.enhance(valueNode, rowKind, targetRowKind, fieldsToAdd);

                // Serialize back to JSON string
                String updatedValueJson = JsonUtils.toJsonString(enhancedNode);

                // Create output row with same field count (value field is replaced)
                Object[] outputFields = new Object[inputRow.getFields().length];
                System.arraycopy(
                        inputRow.getFields(), 0, outputFields, 0, inputRow.getFields().length);
                outputFields[valueFieldIndex] = updatedValueJson;

                SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
                outputRow.setTableId(inputRow.getTableId());
                outputRow.setRowKind(targetRowKind);

                return outputRow;
            } catch (CdcJsonEnhanceException e) {
                log.error("Failed to enhance CDC JSON value field", e);
                throw new CdcJsonEnhanceException("Failed to enhance CDC JSON value field", e);
            } catch (Exception e) {
                log.error("Failed to enhance CDC JSON value field", e);
                throw new CdcJsonEnhanceException("Failed to enhance CDC JSON value field", e);
            }
        }

        /**
         * Split UPDATE event into BEFORE and AFTER records for CDC JSON formats in APPEND_MODE.
         *
         * <p><b>IMPORTANT:</b> Despite the method name containing "Debezium", this method supports
         * multiple CDC formats including Debezium, Compatible Debezium, Canal, OGG, and Kingbase.
         * The method name is historical and the implementation handles format-specific behaviors:
         *
         * <p><b>Standard single-record split (Debezium, OGG, Kingbase, etc.):</b>
         *
         * <ol>
         *   <li>BEFORE record: payload.before data moved to payload.after, op="c",
         *       marker=update_before_marker_value
         *   <li>AFTER record: payload.after data stays, op="c", marker=update_after_marker_value
         * </ol>
         *
         * <p><b>Canal multi-record split (Canal format only):</b>
         *
         * <ul>
         *   <li>Detects if Canal JSON contains multiple records in data[] array (via {@code
         *       getDataArraySize()})
         *   <li>Routes to {@link #splitCanalMultiRecords} for special handling
         *   <li>Outputs 2*N records where N is the size of data[] array
         *   <li>For each data[i]: constructs BEFORE (if old[i] exists) and AFTER records
         * </ul>
         *
         * <p>Both record types:
         *
         * <ul>
         *   <li>Set RowKind to INSERT (Debezium op="c" means INSERT/CREATE)
         *   <li>Keep payload.before = null (follows Debezium spec: INSERT has no before)
         *   <li>Add operation_time and dml_marker_field inside JSON
         * </ul>
         *
         * @param inputRow Original input row
         * @param rowKind Normalized RowKind (UPDATE_BEFORE or UPDATE_AFTER)
         * @param valueField Original CDC JSON value field
         * @param valueNode Parsed JSON node
         * @param enhancer CDC format enhancer (must implement {@link IUpdateSplittableEnhancer})
         * @return List containing split records (2 for standard formats, 2*N for Canal
         *     multi-record)
         */
        private List<SeaTunnelRow> splitUpdateDebeziumJson(
                SeaTunnelRow inputRow,
                RowKind rowKind,
                Object valueField,
                JsonNode valueNode,
                IUpdateSplittableEnhancer enhancer) {

            try {
                // Check if this is Canal format with multiple records
                if (enhancer instanceof CanalJsonEnhancer) {
                    int dataArraySize = ((CanalJsonEnhancer) enhancer).getDataArraySize(valueNode);
                    if (dataArraySize > 1) {
                        log.info(
                                "Canal JSON contains {} records, will split each into BEFORE/AFTER (total {} output records)",
                                dataArraySize,
                                dataArraySize * 2);
                        return splitCanalMultiRecords(
                                inputRow, valueNode, (CanalJsonEnhancer) enhancer, dataArraySize);
                    }
                }

                // Standard single-record split logic
                List<SeaTunnelRow> results = new ArrayList<>(2);
                JsonNode beforeData = enhancer.getBeforeData(valueNode);
                JsonNode afterData = enhancer.getAfterData(valueNode);

                // If both before and after are null/missing, fallback to single output to avoid
                // data loss
                boolean hasBeforeData = beforeData != null && !beforeData.isNull();
                boolean hasAfterData = afterData != null && !afterData.isNull();
                if (!hasBeforeData && !hasAfterData) {
                    log.warn(
                            "Both before and after data are null in UPDATE event, fallback to single output");
                    SeaTunnelRow result =
                            appendModeDebeziumJson(
                                    inputRow, rowKind, valueField, valueNode, enhancer);
                    return result == null
                            ? Collections.emptyList()
                            : Collections.singletonList(result);
                }

                String timestamp = LocalDateTime.now().format(getTimestampFormatter());

                // Log warning if UPDATE event has partial data
                if (hasBeforeData && !hasAfterData) {
                    log.warn(
                            "UPDATE event has BEFORE but missing AFTER data for table {}, will only output BEFORE record",
                            inputRow.getTableId());
                } else if (!hasBeforeData && hasAfterData) {
                    log.warn(
                            "UPDATE event has AFTER but missing BEFORE data for table {}, will only output AFTER record",
                            inputRow.getTableId());
                }

                // === Construct BEFORE record ===
                if (beforeData != null && !beforeData.isNull()) {
                    JsonNode beforeRecord =
                            buildSplitRecord(
                                    valueNode,
                                    beforeData,
                                    timestamp,
                                    config.getUpdateBeforeMarkerValue(),
                                    enhancer);
                    String beforeJson = JsonUtils.toJsonString(beforeRecord);

                    Object[] beforeFields = new Object[inputRow.getFields().length];
                    System.arraycopy(
                            inputRow.getFields(), 0, beforeFields, 0, inputRow.getFields().length);
                    beforeFields[valueFieldIndex] = beforeJson;

                    SeaTunnelRow beforeRow = new SeaTunnelRow(beforeFields);
                    beforeRow.setTableId(inputRow.getTableId());
                    beforeRow.setRowKind(RowKind.INSERT);
                    results.add(beforeRow);
                }

                // === Construct AFTER record ===
                if (afterData != null && !afterData.isNull()) {
                    JsonNode afterRecord =
                            buildSplitRecord(
                                    valueNode,
                                    afterData,
                                    timestamp,
                                    config.getUpdateAfterMarkerValue(),
                                    enhancer);
                    String afterJson = JsonUtils.toJsonString(afterRecord);

                    Object[] afterFields = new Object[inputRow.getFields().length];
                    System.arraycopy(
                            inputRow.getFields(), 0, afterFields, 0, inputRow.getFields().length);
                    afterFields[valueFieldIndex] = afterJson;

                    SeaTunnelRow afterRow = new SeaTunnelRow(afterFields);
                    afterRow.setTableId(inputRow.getTableId());
                    afterRow.setRowKind(RowKind.INSERT);
                    results.add(afterRow);
                }

                return results;

            } catch (Exception e) {
                log.error("Failed to split UPDATE event into BEFORE/AFTER", e);
                // Fallback to single output
                SeaTunnelRow result =
                        appendModeDebeziumJson(inputRow, rowKind, valueField, valueNode, enhancer);
                return result == null ? Collections.emptyList() : Collections.singletonList(result);
            }
        }

        /**
         * Split Canal multi-record message into multiple BEFORE/AFTER pairs
         *
         * @param inputRow Original input row
         * @param valueNode Canal JSON node
         * @param enhancer Canal enhancer
         * @param recordCount Number of records in data array
         * @return List of split records (2 * recordCount)
         */
        private List<SeaTunnelRow> splitCanalMultiRecords(
                SeaTunnelRow inputRow,
                JsonNode valueNode,
                CanalJsonEnhancer enhancer,
                int recordCount) {

            List<SeaTunnelRow> results = new ArrayList<>(recordCount * 2);
            String timestamp = LocalDateTime.now().format(getTimestampFormatter());

            try {
                // Process each record in data array
                for (int i = 0; i < recordCount; i++) {
                    JsonNode afterData = enhancer.getDataAtIndex(valueNode, i);
                    JsonNode beforeData = enhancer.getBeforeDataAtIndex(valueNode, i);

                    if (afterData == null || afterData.isNull()) {
                        log.warn("Canal data[{}] is null, skipping this record in split", i);
                        continue;
                    }

                    // === Construct BEFORE record ===
                    if (beforeData != null && !beforeData.isNull()) {
                        JsonNode beforeRecord =
                                buildSplitRecord(
                                        valueNode,
                                        beforeData,
                                        timestamp,
                                        config.getUpdateBeforeMarkerValue(),
                                        enhancer);
                        String beforeJson = JsonUtils.toJsonString(beforeRecord);

                        Object[] beforeFields = new Object[inputRow.getFields().length];
                        System.arraycopy(
                                inputRow.getFields(),
                                0,
                                beforeFields,
                                0,
                                inputRow.getFields().length);
                        beforeFields[valueFieldIndex] = beforeJson;

                        SeaTunnelRow beforeRow = new SeaTunnelRow(beforeFields);
                        beforeRow.setTableId(inputRow.getTableId());
                        beforeRow.setRowKind(RowKind.INSERT);
                        results.add(beforeRow);
                    } else {
                        // Canal old[] may not align with data[] size or contain nulls in both
                        // INSERT and UPDATE scenarios:
                        // - INSERT: no before state exists (expected)
                        // - UPDATE: old[] may be shorter than data[] or contain nulls (Canal
                        // protocol allows this)
                        // When BEFORE is missing, we only output AFTER to avoid generating
                        // incomplete split records
                        log.warn(
                                "Canal record[{}] has no BEFORE data (old[{}] is missing/null/non-object), only AFTER will be output. "
                                        + "This is expected for INSERT operations, but may also occur in UPDATE scenarios if old[] array "
                                        + "does not align with data[] array. Consider monitoring BEFORE/AFTER output ratio in production. "
                                        + "Table: {}",
                                i,
                                i,
                                inputRow.getTableId());
                    }

                    // === Construct AFTER record ===
                    JsonNode afterRecord =
                            buildSplitRecord(
                                    valueNode,
                                    afterData,
                                    timestamp,
                                    config.getUpdateAfterMarkerValue(),
                                    enhancer);
                    String afterJson = JsonUtils.toJsonString(afterRecord);

                    Object[] afterFields = new Object[inputRow.getFields().length];
                    System.arraycopy(
                            inputRow.getFields(), 0, afterFields, 0, inputRow.getFields().length);
                    afterFields[valueFieldIndex] = afterJson;

                    SeaTunnelRow afterRow = new SeaTunnelRow(afterFields);
                    afterRow.setTableId(inputRow.getTableId());
                    afterRow.setRowKind(RowKind.INSERT);
                    results.add(afterRow);
                }

                return results;

            } catch (Exception e) {
                log.error("Failed to split Canal multi-record message", e);
                // Fallback to single output on error
                // Extract valueField from inputRow to ensure proper enhancement in fallback
                Object valueField =
                        (valueFieldIndex >= 0 && valueFieldIndex < inputRow.getFields().length)
                                ? inputRow.getFields()[valueFieldIndex]
                                : null;
                SeaTunnelRow result =
                        appendModeDebeziumJson(
                                inputRow, RowKind.UPDATE_AFTER, valueField, valueNode, enhancer);
                return result == null ? Collections.emptyList() : Collections.singletonList(result);
            }
        }

        /**
         * Build a single split record (BEFORE or AFTER) for UPDATE splitting.
         *
         * <p>Creates a new CDC JSON structure with:
         *
         * <ul>
         *   <li>payload.before = null (Debezium spec: INSERT has no before)
         *   <li>payload.after = provided data + operation_time + dml_marker_field
         *   <li>payload.op = "c" (CREATE/INSERT)
         *   <li>Preserves other payload metadata fields (source, ts_ms, transaction, etc.)
         * </ul>
         *
         * @param originalNode Original CDC JSON node
         * @param dataNode Data to use (either before or after data from original UPDATE)
         * @param timestamp Formatted timestamp string
         * @param markerValue DML marker value (BEFORE or AFTER)
         * @param enhancer CDC format enhancer
         * @return New CDC JSON node
         */
        private JsonNode buildSplitRecord(
                JsonNode originalNode,
                JsonNode dataNode,
                String timestamp,
                String markerValue,
                IUpdateSplittableEnhancer enhancer)
                throws CdcJsonEnhanceException {

            // Clone the original node to preserve top-level metadata
            org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode clonedNode =
                    (org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode)
                            originalNode.deepCopy();

            // Build fields to add into the data
            java.util.Map<String, Object> fieldsToAdd = new java.util.HashMap<>();
            if (config.getTimestampEnabled()) {
                fieldsToAdd.put(config.getTimestampFieldName(), timestamp);
            }
            if (config.getDmlMarkerEnabled()) {
                fieldsToAdd.put(config.getDmlMarkerFieldName(), markerValue);
            }

            // Enhance the data node with additional fields
            JsonNode enhancedData = enhancer.addFieldsToData(dataNode, fieldsToAdd);

            JsonNode newPayload = enhancer.buildPayload(null, enhancedData, "c");
            return enhancer.replacePayload(clonedNode, newPayload);
        }

        /*
         * APPEND_MODE for flat data: append timestamp and DML marker columns externally
         *
         * Adds operation_time (timestamp) and dml_marker_field columns.
         * All non-READ rows are emitted as INSERT; READ rows keep RowKind.READ so that downstream
         * components can distinguish snapshot from incremental events while still treating READ as
         * an insert operation.
         */
        private SeaTunnelRow appendModeFlat(SeaTunnelRow inputRow, RowKind rowKind) {
            int inputFieldLength = inputCatalogTable.getTableSchema().getColumns().size();
            int outputFieldLength = inputFieldLength + 2;

            Object[] outputFields = new Object[outputFieldLength];
            System.arraycopy(inputRow.getFields(), 0, outputFields, 0, inputFieldLength);

            // Add current timestamp
            outputFields[timestampFieldIndex] = LocalDateTime.now();

            // Add DML marker based on original RowKind
            String dmlMarker = getDMLMarkerValue(rowKind);
            outputFields[dmlMarkerFieldIndex] = dmlMarker;

            SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
            outputRow.setTableId(inputRow.getTableId());
            RowKind targetRowKind = (rowKind == RowKind.READ) ? RowKind.READ : RowKind.INSERT;
            outputRow.setRowKind(targetRowKind);

            return outputRow;
        }

        /*
         * ADD_DML_MARKER mode entry: route to CDC JSON or flat implementation
         *
         * Optionally adds dml_marker_field and/or operation_time based on configuration.
         * DELETE rows are converted to UPDATE_AFTER.
         * Other rows keep original RowKind.
         */
        private SeaTunnelRow addDMLMarkerCdcJson(
                SeaTunnelRow inputRow,
                RowKind rowKind,
                Object valueField,
                JsonNode valueNode,
                ICdcJsonEnhancer enhancer) {
            if (valueField == null) {
                log.warn("CDC value field is null, skip enhancement");
                return inputRow;
            }

            if (valueNode == null) {
                log.warn("Failed to parse CDC JSON, skip enhancement");
                return inputRow;
            }

            if (enhancer == null) {
                log.warn("No CDC format enhancer found, skip enhancement");
                return inputRow;
            }

            try {
                java.util.Map<String, Object> fieldsToAdd = new java.util.HashMap<>();
                if (config.getDmlMarkerEnabled()) {
                    String dmlMarker = getDMLMarkerValue(rowKind);
                    // If split_update is enabled but we are here (single row processing),
                    // we should fallback to UPDATE marker for update events to avoid misleading
                    // "before/after" marker
                    // on a single composite event.
                    if (config.getSplitUpdate()
                            && (rowKind == RowKind.UPDATE_BEFORE
                                    || rowKind == RowKind.UPDATE_AFTER)) {
                        dmlMarker = config.getUpdateMarkerValue();
                    }
                    fieldsToAdd.put(config.getDmlMarkerFieldName(), dmlMarker);
                }
                if (config.getTimestampEnabled()) {
                    fieldsToAdd.put(
                            config.getTimestampFieldName(),
                            LocalDateTime.now().format(getTimestampFormatter()));
                }

                RowKind targetRowKind =
                        (rowKind == RowKind.DELETE) ? RowKind.UPDATE_AFTER : rowKind;

                JsonNode enhancedNode =
                        enhancer.enhance(valueNode, rowKind, targetRowKind, fieldsToAdd);

                String updatedValueJson = JsonUtils.toJsonString(enhancedNode);

                Object[] outputFields = new Object[inputRow.getFields().length];
                System.arraycopy(
                        inputRow.getFields(), 0, outputFields, 0, inputRow.getFields().length);
                outputFields[valueFieldIndex] = updatedValueJson;

                SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
                outputRow.setTableId(inputRow.getTableId());
                outputRow.setRowKind(targetRowKind);

                return outputRow;
            } catch (CdcJsonEnhanceException e) {
                log.error("Failed to enhance CDC JSON for ADD_DML_MARKER", e);
                throw new CdcJsonEnhanceException("Failed to enhance CDC JSON for ADD_DML_MARKER", e);
            } catch (Exception e) {
                log.error("Failed to enhance CDC JSON for ADD_DML_MARKER", e);
                throw new CdcJsonEnhanceException("Failed to enhance CDC JSON for ADD_DML_MARKER", e);
            }
        }

        private SeaTunnelRow addDMLMarkerFlat(SeaTunnelRow inputRow, RowKind rowKind) {
            int inputFieldLength = inputCatalogTable.getTableSchema().getColumns().size();
            int addedFields = 0;

            if (config.getDmlMarkerEnabled()) {
                addedFields++;
            }
            if (config.getTimestampEnabled()) {
                addedFields++;
            }

            int outputFieldLength = inputFieldLength + addedFields;
            Object[] outputFields = new Object[outputFieldLength];
            System.arraycopy(inputRow.getFields(), 0, outputFields, 0, inputFieldLength);

            if (config.getDmlMarkerEnabled()) {
                String dmlMarker = getDMLMarkerValue(rowKind);
                outputFields[dmlMarkerFieldIndex] = dmlMarker;
            }

            if (config.getTimestampEnabled()) {
                outputFields[timestampFieldIndex] = LocalDateTime.now();
            }

            SeaTunnelRow outputRow = new SeaTunnelRow(outputFields);
            outputRow.setTableId(inputRow.getTableId());

            if (rowKind == RowKind.DELETE) {
                outputRow.setRowKind(RowKind.UPDATE_AFTER);
            } else {
                outputRow.setRowKind(inputRow.getRowKind());
            }

            return outputRow;
        }

        /*
         * Parse the configured CDC value field into a JsonNode
         *
         * Supports multiple input types:
         * - String: JSON string
         * - byte[]: UTF-8 encoded JSON bytes
         * - Object: POJO to be serialized to JSON
         */
        private JsonNode parseValueFieldToJsonNode(Object valueField) {
            try {
                if (valueField instanceof JsonNode) {
                    return (JsonNode) valueField;
                } else if (valueField instanceof String) {
                    return JsonUtils.parseObject((String) valueField);
                } else if (valueField instanceof byte[]) {
                    String jsonString = new String((byte[]) valueField, StandardCharsets.UTF_8);
                    return JsonUtils.parseObject(jsonString);
                } else {
                    return JsonUtils.toJsonNode(valueField);
                }
            } catch (Exception e) {
                log.error("Failed to parse value field to JsonNode", e);
                return null;
            }
        }

        /*
         * Build a timestamp formatter based on configured precision.
         *
         * Precision examples:
         * - 0  -> yyyy-MM-dd HH:mm:ss
         * - 3  -> yyyy-MM-dd HH:mm:ss.SSS
         * - 6  -> yyyy-MM-dd HH:mm:ss.SSSSSS
         * - 9  -> yyyy-MM-dd HH:mm:ss.SSSSSSSSS
         */
        private java.time.format.DateTimeFormatter buildTimestampFormatter() {
            int precision = config.getTimestampPrecision();
            if (precision == 0) {
                return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            } else {
                return java.time.format.DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd HH:mm:ss." + repeatChar('S', precision));
            }
        }

        /*
         * Repeat a single character to construct a pattern fragment
         */
        private String repeatChar(char c, int count) {
            StringBuilder sb = new StringBuilder(count);
            for (int i = 0; i < count; i++) {
                sb.append(c);
            }
            return sb.toString();
        }

        /**
         * Get timestamp formatter with lazy initialization.
         *
         * <p>This method is used to access the transient timestampFormatter field. If the formatter
         * hasn't been initialized (e.g., after deserialization), it will be created on demand.
         *
         * @return DateTimeFormatter for formatting timestamps
         */
        private java.time.format.DateTimeFormatter getTimestampFormatter() {
            if (timestampFormatter == null) {
                timestampFormatter = buildTimestampFormatter();
            }
            return timestampFormatter;
        }

        /*
         * Get DML marker value based on RowKind and split_update configuration
         *
         * When split_update is enabled, UPDATE is split into UPDATE_BEFORE and UPDATE_AFTER.
         * When split_update is disabled, both UPDATE_BEFORE and UPDATE_AFTER use UPDATE marker.
         */
        private String getDMLMarkerValue(RowKind rowKind) {
            switch (rowKind) {
                case INSERT:
                    return config.getInsertMarkerValue();
                case READ:
                    return config.getReadMarkerValue();
                case UPDATE_BEFORE:
                    return config.getSplitUpdate()
                            ? config.getUpdateBeforeMarkerValue()
                            : config.getUpdateMarkerValue();
                case UPDATE_AFTER:
                    return config.getSplitUpdate()
                            ? config.getUpdateAfterMarkerValue()
                            : config.getUpdateMarkerValue();
                case DELETE:
                    return config.getDeleteMarkerValue();
                default:
                    return "UNKNOWN";
            }
        }

        /*
         * Detect CDC format enhancer with caching for better performance
         *
         * Uses a simple cache strategy:
         * - First successful detection on one row "locks" the enhancer for this table
         * - Subsequent rows reuse the cached enhancer directly without re-running canHandle()
         */
        private ICdcJsonEnhancer detectEnhancerWithCache(JsonNode valueNode) {
            if (valueNode == null) {
                return null;
            }

            // If we've already determined the enhancer for this table, just reuse it.
            if (enhancerLocked) {
                return cachedEnhancer;
            }

            // Try cached enhancer first for the initial detection
            ICdcJsonEnhancer enhancer = cachedEnhancer;
            if (enhancer != null && enhancer.canHandle(valueNode)) {
                enhancerLocked = true;
                return enhancer;
            }

            // No cached enhancer yet or it cannot handle this JSON - detect once
            enhancer = enhancerManager.detectEnhancer(valueNode);
            if (enhancer != null) {
                cachedEnhancer = enhancer;
                enhancerLocked = true;
            }

            return enhancer;
        }

        /*
         * Initialize CDC JSON enhancer based on explicit cdc_json_format configuration.
         *
         * When user configures cdc_json_format, we:
         * - Only apply it when a CDC value field is detected (valueFieldIndex >= 0)
         * - Create corresponding enhancer implementation
         * - Lock the enhancer so that runtime auto-detection is completely skipped
         *
         * When cdc_json_format is not configured, the enhancer will be determined lazily on the
         * first CDC JSON row via detectEnhancerWithCache().
         */
        private void initializeEnhancerFromConfig() {
            String format = config.getCdcJsonFormat();
            if (format == null || format.trim().isEmpty()) {
                return;
            }

            if (valueFieldIndex < 0) {
                log.warn(
                        "cdc_json_format '{}' is configured but no CDC JSON value field is found for table {}, JSON enhancement will be skipped",
                        format,
                        inputCatalogTable.getTableId());
                return;
            }

            String upper = format.trim().toUpperCase();
            ICdcJsonEnhancer enhancer = createEnhancerByFormat(upper);
            if (enhancer == null) {
                // CRITICAL: When user explicitly sets cdc_json_format, unsupported format should
                // fail-fast
                // rather than silently fallback to auto-detection (user won't know their config is
                // ignored)
                throw new IllegalArgumentException(
                        String.format(
                                "Unsupported cdc_json_format '%s' for table %s. "
                                        + "Supported formats: DEBEZIUM_JSON, COMPATIBLE_DEBEZIUM_JSON, CANAL_JSON, OGG_JSON, KINGBASE_JSON, CUSTOM_JSON, CUSTOM_CDC_JSON. "
                                        + "Please check for typos or remove cdc_json_format to use runtime auto-detection.",
                                format, inputCatalogTable.getTableId()));
            }

            this.cachedEnhancer = enhancer;
            this.enhancerLocked = true;

            log.info(
                    "Use explicit cdc_json_format '{}' -> {} for table {}",
                    upper,
                    enhancer.getFormatName(),
                    inputCatalogTable.getTableId());
        }

        /**
         * Create CDC JSON enhancer by logical format name used in cdc_json_format.
         *
         * <p>⚠️ IMPORTANT: When adding a new CDC JSON format, add a case here and ensure the value
         * matches the documentation of cdc_json_format.
         *
         * @param formatName Format name from cdc_json_format configuration
         * @return Corresponding enhancer instance, or null if format is unknown
         */
        private ICdcJsonEnhancer createEnhancerByFormat(String formatName) {
            switch (formatName) {
                case "DEBEZIUM_JSON":
                    return new DebeziumJsonEnhancer();
                case "COMPATIBLE_DEBEZIUM_JSON":
                    return new CompatibleDebeziumJsonEnhancer();
                case "CANAL_JSON":
                    return new CanalJsonEnhancer();
                case "OGG_JSON":
                    return new OggJsonEnhancer();
                case "KINGBASE_JSON":
                    return new KingbaseJsonEnhancer();
                case "CUSTOM_JSON":
                    return new org.apache.seatunnel.transform.dmleventfilter.jsonenhancer
                            .CustomJsonEnhancer();
                case "CUSTOM_CDC_JSON":
                    CustomCdcConfig cdcConfig = buildCustomCdcConfig(this.config);
                    if (cdcConfig == null) {
                        // CRITICAL: When user explicitly sets cdc_json_format=CUSTOM_CDC_JSON,
                        // incomplete configuration should fail-fast rather than silently fallback
                        throw new IllegalArgumentException(
                                String.format(
                                        "cdc_json_format=CUSTOM_CDC_JSON is explicitly set for table %s, but custom CDC configuration is incomplete or invalid. "
                                                + "Please provide all required custom CDC configuration fields (op_field_name, before_field_name, after_field_name, etc.), "
                                                + "or remove cdc_json_format to use runtime auto-detection.",
                                        inputCatalogTable.getTableId()));
                    }
                    return new org.apache.seatunnel.transform.dmleventfilter.jsonenhancer
                            .CustomCdcJsonEnhancer(cdcConfig);
                default:
                    return null;
            }
        }

        /*
         * Initialize CDC value field index by scanning column names
         *
         * Searches for a column matching configured cdc_value_field_names (default: ["value"]).
         * If found, sets valueFieldIndex to indicate CDC JSON format is detected.
         * If not found, leaves valueFieldIndex as -1 to indicate flat data format.
         *
         * This detection determines whether to enhance CDC JSON internally or append columns externally.
         */
        private void initializeValueFieldIndex() {
            List<Column> columns = inputCatalogTable.getTableSchema().getColumns();
            if (columns.isEmpty() || cdcValueFieldNames == null || cdcValueFieldNames.isEmpty()) {
                return;
            }
            // Try each candidate field name
            for (String candidate : cdcValueFieldNames) {
                if (candidate == null) {
                    continue;
                }
                // Scan all columns for case-insensitive match
                for (int i = 0; i < columns.size(); i++) {
                    if (candidate.equalsIgnoreCase(columns.get(i).getName())) {
                        valueFieldIndex = i;
                        log.info(
                                "Detected CDC JSON value field '{}' (index {}) for table {}",
                                columns.get(i).getName(),
                                i,
                                inputCatalogTable.getTableId());
                        return;
                    }
                }
            }
            // No match found: flat data format
            log.info(
                    "No CDC JSON value field matched candidates {} in table {}",
                    cdcValueFieldNames,
                    inputCatalogTable.getTableId());
        }

        /*
         * Initialize field indexes for added columns after schema is built
         *
         * Only applies to flat data format (valueFieldIndex < 0) in new mode.
         * For CDC JSON format, fields are added inside value JSON, no external columns.
         *
         * Field indexes are calculated based on:
         * - inputFieldCount: number of input columns
         * - Processing mode: determines which columns are added and their order
         */
        private void initializeFieldIndexes() {
            if (config.getProcessingMode() == null) {
                // Legacy mode: no field indexes needed
                return;
            }

            if (valueFieldIndex >= 0) {
                // CDC JSON format: fields added internally, no external field indexes
                return;
            }

            // Flat data format: calculate field indexes for appended columns
            int inputFieldCount = inputCatalogTable.getTableSchema().getColumns().size();

            switch (config.getProcessingMode()) {
                case SOFT_DELETE:
                    // Appends 1 column: marker_field
                    markerFieldIndex = inputFieldCount;
                    break;
                case APPEND_MODE:
                    // Appends 2 columns: operation_time, dml_marker_field
                    timestampFieldIndex = inputFieldCount;
                    dmlMarkerFieldIndex = inputFieldCount + 1;
                    break;
                case ADD_DML_MARKER:
                    // Appends 0-2 columns based on configuration
                    int currentIndex = inputFieldCount;
                    if (config.getDmlMarkerEnabled()) {
                        dmlMarkerFieldIndex = currentIndex++;
                    }
                    if (config.getTimestampEnabled()) {
                        timestampFieldIndex = currentIndex;
                    }
                    break;
                default:
                    break;
            }
        }

        /*
         * Build output catalog table with potentially modified schema
         *
         * Output table has same identifier, options, partition keys, and comment as input.
         * Only the table schema may be different (with added columns).
         */
        private CatalogTable buildOutputCatalogTable() {
            TableIdentifier tableIdentifier = inputCatalogTable.getTableId().copy();
            TableSchema tableSchema = buildOutputTableSchema();
            return CatalogTable.of(
                    tableIdentifier,
                    tableSchema,
                    inputCatalogTable.getOptions(),
                    inputCatalogTable.getPartitionKeys(),
                    inputCatalogTable.getComment());
        }

        /*
         * Build output table schema with added columns
         *
         * Legacy mode: keep original schema unchanged
         * New mode: add columns based on processing mode and CDC format detection
         *
         * Schema modification rules:
         * 1. FILTER_DML: no schema change
         * 2. SOFT_DELETE:
         *    - CDC JSON: no external schema change (marker added inside value JSON)
         *    - Flat data: append marker_field column
         * 3. APPEND_MODE:
         *    - CDC JSON: no external schema change (timestamp+marker added inside value JSON)
         *    - Flat data: append operation_time and dml_marker_field columns, modify primary key
         * 4. ADD_DML_MARKER:
         *    - CDC JSON: no external schema change (fields added inside value JSON)
         *    - Flat data: append dml_marker_field and/or operation_time based on configuration
         */
        private TableSchema buildOutputTableSchema() {
            if (config.getProcessingMode() == null) {
                // Legacy mode: keep original schema
                log.info(
                        "DMLEventFilterTransform buildOutputTableSchema in legacy mode (no processing_mode), keep original schema: {} columns for table {}",
                        inputCatalogTable.getTableSchema().getColumns().size(),
                        inputCatalogTable.getTableId());
                return inputCatalogTable.getTableSchema();
            }

            log.info(
                    "DMLEventFilterTransform buildOutputTableSchema with processing_mode={} for table {}",
                    config.getProcessingMode(),
                    inputCatalogTable.getTableId());

            List<Column> inputColumns = inputCatalogTable.getTableSchema().getColumns();
            List<Column> outputColumns = new ArrayList<>();

            // Copy all input columns
            for (Column column : inputColumns) {
                outputColumns.add(column.copy());
            }

            // CDC JSON format: fields are added inside the value JSON, so external schema must
            // remain unchanged.
            boolean isCdcJsonFormat = valueFieldIndex >= 0;

            switch (config.getProcessingMode()) {
                case FILTER_DML:
                    break;
                case SOFT_DELETE:
                    if (isCdcJsonFormat) {
                        break;
                    }
                    String markerFieldName = config.getMarkerFieldName();
                    checkFieldNameDuplicate(outputColumns, markerFieldName);

                    PhysicalColumn markerColumn =
                            PhysicalColumn.of(
                                    markerFieldName,
                                    BasicType.STRING_TYPE,
                                    Long.valueOf(config.getMarkerFieldLength()),
                                    true,
                                    null,
                                    null);
                    outputColumns.add(markerColumn);
                    break;
                case APPEND_MODE:
                    if (isCdcJsonFormat) {
                        break;
                    }
                    String timestampFieldName = config.getTimestampFieldName();
                    String dmlMarkerFieldName = config.getDmlMarkerFieldName();

                    checkFieldNameDuplicate(outputColumns, timestampFieldName);
                    checkFieldNameDuplicate(outputColumns, dmlMarkerFieldName);

                    PhysicalColumn timestampColumn =
                            PhysicalColumn.of(
                                    timestampFieldName,
                                    LocalTimeType.LOCAL_DATE_TIME_TYPE,
                                    null,
                                    Integer.valueOf(config.getTimestampPrecision()),
                                    false,
                                    null,
                                    null);
                    outputColumns.add(timestampColumn);

                    boolean dmlMarkerIsPrimaryKey =
                            Boolean.TRUE.equals(config.getDmlMarkerIsPrimaryKey());
                    boolean timestampIsPrimaryKey =
                            Boolean.TRUE.equals(config.getTimestampIsPrimaryKey());
                    if (Boolean.TRUE.equals(config.getSplitUpdate())
                            && timestampIsPrimaryKey
                            && !dmlMarkerIsPrimaryKey) {
                        dmlMarkerIsPrimaryKey = true;
                    }

                    PhysicalColumn dmlMarkerColumn =
                            PhysicalColumn.of(
                                    dmlMarkerFieldName,
                                    BasicType.STRING_TYPE,
                                    Long.valueOf(config.getMarkerFieldLength()),
                                    !dmlMarkerIsPrimaryKey,
                                    null,
                                    null);
                    outputColumns.add(dmlMarkerColumn);

                    break;
                case ADD_DML_MARKER:
                    if (isCdcJsonFormat) {
                        break;
                    }
                    if (config.getDmlMarkerEnabled()) {
                        String dmlFieldName = config.getDmlMarkerFieldName();
                        checkFieldNameDuplicate(outputColumns, dmlFieldName);

                        PhysicalColumn dmlColumn =
                                PhysicalColumn.of(
                                        dmlFieldName,
                                        BasicType.STRING_TYPE,
                                        Long.valueOf(config.getMarkerFieldLength()),
                                        true,
                                        null,
                                        null);
                        outputColumns.add(dmlColumn);
                    }

                    if (config.getTimestampEnabled()) {
                        String tsFieldName = config.getTimestampFieldName();
                        checkFieldNameDuplicate(outputColumns, tsFieldName);

                        PhysicalColumn tsColumn =
                                PhysicalColumn.of(
                                        tsFieldName,
                                        LocalTimeType.LOCAL_DATE_TIME_TYPE,
                                        null,
                                        Integer.valueOf(config.getTimestampPrecision()),
                                        true,
                                        null,
                                        null);
                        outputColumns.add(tsColumn);
                    }
                    break;
            }

            TableSchema.Builder builder = TableSchema.builder();
            builder.columns(outputColumns);

            if (config.getProcessingMode() == ProcessingMode.APPEND_MODE) {
                if (valueFieldIndex >= 0) {
                    if (inputCatalogTable.getTableSchema().getPrimaryKey() != null) {
                        builder.primaryKey(
                                inputCatalogTable.getTableSchema().getPrimaryKey().copy());
                    }
                } else {
                    PrimaryKey oldPrimaryKey = inputCatalogTable.getTableSchema().getPrimaryKey();
                    if (oldPrimaryKey != null) {
                        List<String> newPrimaryKeyColumns =
                                new ArrayList<>(oldPrimaryKey.getColumnNames());
                        boolean timestampIsPrimaryKey =
                                Boolean.TRUE.equals(config.getTimestampIsPrimaryKey());
                        boolean dmlMarkerIsPrimaryKey =
                                Boolean.TRUE.equals(config.getDmlMarkerIsPrimaryKey());
                        if (Boolean.TRUE.equals(config.getSplitUpdate())
                                && timestampIsPrimaryKey
                                && !dmlMarkerIsPrimaryKey) {
                            dmlMarkerIsPrimaryKey = true;
                        }

                        if (timestampIsPrimaryKey) {
                            newPrimaryKeyColumns.add(config.getTimestampFieldName());
                        }
                        if (dmlMarkerIsPrimaryKey) {
                            newPrimaryKeyColumns.add(config.getDmlMarkerFieldName());
                        }
                        PrimaryKey newPrimaryKey =
                                PrimaryKey.of(oldPrimaryKey.getPrimaryKey(), newPrimaryKeyColumns);
                        builder.primaryKey(newPrimaryKey);
                        log.info(
                                "APPEND_MODE: Set composite primary key {} for DDL generation for table {}",
                                newPrimaryKeyColumns,
                                inputCatalogTable.getTableId());
                    }
                }
            } else {
                if (inputCatalogTable.getTableSchema().getPrimaryKey() != null) {
                    builder.primaryKey(inputCatalogTable.getTableSchema().getPrimaryKey().copy());
                }
            }

            List<ConstraintKey> constraintKeys =
                    inputCatalogTable.getTableSchema().getConstraintKeys().stream()
                            .map(ConstraintKey::copy)
                            .collect(Collectors.toList());
            builder.constraintKey(constraintKeys);

            TableSchema outputSchema = builder.build();
            log.info(
                    "Transformed table schema from {} columns to {} columns for table {}",
                    inputColumns.size(),
                    outputColumns.size(),
                    inputCatalogTable.getTableId());

            return outputSchema;
        }

        /*
         * Check if a field name already exists in the schema (case-insensitive)
         *
         * This matches the behavior of most databases where field names are case-insensitive.
         * Also consistent with frontend validation logic.
         *
         * Throws TransformCommonError.duplicateFieldName if duplicate is found.
         */
        private void checkFieldNameDuplicate(List<Column> existingColumns, String newFieldName) {
            for (Column column : existingColumns) {
                if (column.getName().equalsIgnoreCase(newFieldName)) {
                    throw org.apache.seatunnel.transform.exception.TransformCommonError
                            .duplicateFieldName(newFieldName);
                }
            }
        }

        /*
         * Build custom CDC configuration from transform config
         *
         * Custom CDC format allows users to define their own:
         * - Operation type field name (e.g. "op", "type")
         * - Data field name (e.g. "after", "data")
         * - Operation type to RowKind mapping (e.g. {"c": INSERT, "u": UPDATE, "d": DELETE})
         * - RowKind to operation type reverse mapping (for enhancement)
         *
         * Returns null if configuration is incomplete or not provided.
         */
        private CustomCdcConfig buildCustomCdcConfig(DMLEventFilterTransformConfig config) {
            Map<String, RowKind> mapping = config.getCustomCdcOperationTypeMapping();
            String opField = config.getCustomCdcOperationTypeField();
            String dataField = config.getCustomCdcDataField();
            if (isBlank(opField) || isBlank(dataField) || mapping == null || mapping.isEmpty()) {
                // Incomplete custom CDC config
                return null;
            }

            // Build reverse mapping if not provided
            Map<RowKind, String> reverse = config.getCustomCdcRowKindMapping();
            if (reverse == null || reverse.isEmpty()) {
                reverse = new EnumMap<>(RowKind.class);
                for (Map.Entry<String, RowKind> entry : mapping.entrySet()) {
                    if (!reverse.containsKey(entry.getValue())) {
                        reverse.put(entry.getValue(), entry.getKey());
                    }
                }
            }
            return new CustomCdcConfig(opField, dataField, mapping, reverse);
        }

        /*
         * Check if a string is blank (null or empty/whitespace-only)
         */
        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
