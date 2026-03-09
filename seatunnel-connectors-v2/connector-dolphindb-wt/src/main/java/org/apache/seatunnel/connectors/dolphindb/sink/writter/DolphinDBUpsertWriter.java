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

package org.apache.seatunnel.connectors.dolphindb.sink.writter;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.api.table.type.SqlType;
import org.apache.seatunnel.connectors.dolphindb.exception.DolphinDBConnectorException;
import org.apache.seatunnel.connectors.dolphindb.exception.DolphinDBErrorCode;

import org.apache.commons.lang3.StringUtils;

import com.xxdb.DBConnection;
import com.xxdb.comm.ErrorCodeInfo;
import com.xxdb.data.BasicBoolean;
import com.xxdb.data.BasicTable;
import com.xxdb.data.Entity;
import com.xxdb.data.Vector;
import com.xxdb.multithreadedtablewriter.MultithreadedTableWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.ADDRESS;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.BATCH_SIZE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.DATABASE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.KEY_COL_NAMES;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.PASSWORD;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.TABLE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.USER;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.USE_SSL;

@Slf4j
public class DolphinDBUpsertWriter implements DolphinDBWriter {

    private final CatalogTable catalogTable;
    private final ReadonlyConfig pluginConfig;
    private final SeaTunnelRowType seaTunnelRowType;
    private final MultithreadedTableWriter multithreadedTableWriter;
    private final boolean useLegacyUpsert;
    private final DBConnection legacyConnection;
    private final String legacyUpsertFunctionName;
    private final int legacyBatchSize;
    private final List<Object[]> legacyBuffer;
    private final String[] dolphinDBColumnNames;
    private final int[] dolphinDBColumnToRowIndex;
    private final String[] dolphinDBColumnTypes;
    private final int legacyKeyWriteIndex;

    public DolphinDBUpsertWriter(CatalogTable catalogTable, ReadonlyConfig pluginConfig)
            throws Exception {
        this.catalogTable = catalogTable;
        this.pluginConfig = pluginConfig;
        this.seaTunnelRowType = catalogTable.getTableSchema().toPhysicalRowDataType();
        DolphinDBColumnMapping mapping = resolveDolphinDBColumnMapping();
        this.dolphinDBColumnNames = mapping.columnNames;
        this.dolphinDBColumnToRowIndex = mapping.columnToRowIndex;
        this.dolphinDBColumnTypes = mapping.columnTypes;

        DolphinDBServerInfo serverInfo = parseServerInfo(pluginConfig);
        String version = fetchDolphinDBVersion(serverInfo);
        this.useLegacyUpsert = shouldUseLegacyUpsertForKeyedUpsert(version);
        this.legacyBatchSize = pluginConfig.get(BATCH_SIZE);
        this.legacyBuffer = useLegacyUpsert ? new ArrayList<>(legacyBatchSize) : null;

        if (useLegacyUpsert) {
            this.multithreadedTableWriter = null;
            this.legacyConnection = new DBConnection(false, serverInfo.useSSL);
            if (!legacyConnection.connect(
                    serverInfo.host, serverInfo.port, serverInfo.user, serverInfo.password)) {
                throw new DolphinDBConnectorException(
                        DolphinDBErrorCode.WRITE_DATA_ERROR,
                        "Failed to connect to DolphinDB for legacy upsert: "
                                + serverInfo.host
                                + ":"
                                + serverInfo.port);
            }
            this.legacyKeyWriteIndex = resolveSingleKeyWriteIndex();
            this.legacyUpsertFunctionName =
                    createLegacyUpsertFunction(
                            serverInfo, dolphinDBColumnNames[legacyKeyWriteIndex]);
            log.warn(
                    "DolphinDB server version '{}' does not support keyColNames-based upsert! in this connector; "
                            + "fallback to legacy upsert (delete by key + tableInsert) for table {}.{}",
                    version,
                    pluginConfig.get(DATABASE),
                    pluginConfig.get(TABLE));
        } else {
            this.legacyConnection = null;
            this.legacyUpsertFunctionName = null;
            this.legacyKeyWriteIndex = -1;
            this.multithreadedTableWriter =
                    MultithreadedTableWriterFactory.createMultithreadedTableWriter(pluginConfig);
        }
    }

    @Override
    public void write(SeaTunnelRow seaTunnelRow) {
        // The field will be transformed by BasicEntityFactory.createScalar
        Object[] fields = seaTunnelRow.getFields();
        Object[] finalFields = buildWriteFields(fields);
        validateBoolColumns(fields, finalFields);

        if (useLegacyUpsert) {
            legacyBuffer.add(finalFields);
            if (legacyBuffer.size() >= legacyBatchSize) {
                flushLegacyBuffer();
            }
            return;
        }

        ErrorCodeInfo errorCodeInfo;
        try {
            errorCodeInfo = multithreadedTableWriter.insert(finalFields);

        } catch (RuntimeException e) {
            try {
                String statusString = String.valueOf(multithreadedTableWriter.getStatus());
                if (statusString.contains("Expected: BOOL") || statusString.contains("BOOL")) {
                    StringBuilder details = new StringBuilder();
                    for (int i = 0; i < finalFields.length; i++) {
                        int rowIndex = i;
                        if (dolphinDBColumnToRowIndex != null
                                && i >= 0
                                && i < dolphinDBColumnToRowIndex.length) {
                            rowIndex = dolphinDBColumnToRowIndex[i];
                        }
                        String fieldName = getColumnNameForDebug(i);
                        SqlType sqlType = getColumnSqlTypeForDebug(i);
                        if (sqlType.equals(SqlType.BOOLEAN)
                                || looksLikeBooleanFieldName(fieldName)) {
                            Object raw =
                                    rowIndex >= 0 && rowIndex < fields.length
                                            ? fields[rowIndex]
                                            : null;
                            Object coerced = finalFields[i];
                            details.append("[")
                                    .append(i)
                                    .append(" ")
                                    .append(fieldName)
                                    .append(" type=")
                                    .append(sqlType)
                                    .append(" raw=")
                                    .append(raw)
                                    .append(" rawClass=")
                                    .append(raw == null ? "null" : raw.getClass().getName())
                                    .append(" coerced=")
                                    .append(coerced)
                                    .append(" coercedClass=")
                                    .append(coerced == null ? "null" : coerced.getClass().getName())
                                    .append("] ");
                        }
                    }
                    if (details.length() > 0) {
                        log.error("Potential boolean fields in failed row: {}", details.toString());
                    }
                }
            } catch (Exception ignored) {
                // best effort; never hide the original error
            }
            log.error(
                    "RuntimeException while inserting data into DolphinDB table: {},  status: {}",
                    catalogTable.getTableId().getTableName(),
                    multithreadedTableWriter.getStatus(),
                    e);

            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR,
                    multithreadedTableWriter.getStatus().toString(),
                    e);
        } catch (Exception e) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR, e.getMessage(), e);
        }

        if (errorCodeInfo.hasError()) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR, errorCodeInfo.toString());
        }
    }

    @Override
    public Optional<Void> prepareCommit() throws Exception {
        if (useLegacyUpsert) {
            flushLegacyBuffer();
            return Optional.empty();
        }
        MultithreadedTableWriter.Status status = multithreadedTableWriter.getStatus();
        if (StringUtils.isNotEmpty(status.getErrorCode())) {
            log.error(
                    "MultithreadedTableWriter write data error for table {}: {}",
                    catalogTable.getTableId().getTableName(),
                    status.getErrorCode());
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR, status.getErrorCode());
        }
        return Optional.empty();
    }

    @Override
    public void close() throws Exception {
        if (useLegacyUpsert) {
            try {
                flushLegacyBuffer();
            } finally {
                if (legacyConnection != null) {
                    legacyConnection.close();
                }
            }
            return;
        }
        multithreadedTableWriter.waitForThreadCompletion();
        MultithreadedTableWriter.Status status = multithreadedTableWriter.getStatus();
        if (StringUtils.isNotEmpty(status.getErrorCode())) {
            log.error("MultithreadedTableWriter completion error: {}", status.getErrorCode());
            throw new IOException("Close MultithreadedTableWriter failed" + status.getErrorCode());
        }
    }

    private void flushLegacyBuffer() {
        if (!useLegacyUpsert || legacyBuffer == null || legacyBuffer.isEmpty()) {
            return;
        }
        int rows = legacyBuffer.size();
        if (dolphinDBColumnNames == null || dolphinDBColumnTypes == null) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR,
                    "Legacy upsert requires DolphinDB schema mapping (column names/types).");
        }

        List<String> colNames = Arrays.asList(dolphinDBColumnNames);
        List<Vector> columns = new ArrayList<>(dolphinDBColumnNames.length);
        try {
            for (int col = 0; col < dolphinDBColumnNames.length; col++) {
                Vector vector = createVectorByDolphinType(dolphinDBColumnTypes[col], rows);
                for (int r = 0; r < rows; r++) {
                    Object v = legacyBuffer.get(r)[col];
                    Entity scalar =
                            com.xxdb.data.BasicEntityFactory.createScalar(
                                    vector.getDataType(), v, 0);
                    vector.set(r, scalar);
                }
                columns.add(vector);
            }

            BasicTable tb = new BasicTable(colNames, columns);
            legacyConnection.run(legacyUpsertFunctionName, Arrays.asList((Entity) tb));
            legacyBuffer.clear();
        } catch (Exception e) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR,
                    "Legacy upsert failed: " + e.getMessage(),
                    e);
        }
    }

    private Vector createVectorByDolphinType(String dolphinType, int rows) {
        String t = dolphinType == null ? "" : dolphinType.trim().toUpperCase();
        switch (t) {
            case "BOOL":
            case "BOOLEAN":
                return new com.xxdb.data.BasicBooleanVector(rows);
            case "INT":
                return new com.xxdb.data.BasicIntVector(rows);
            case "LONG":
                return new com.xxdb.data.BasicLongVector(rows);
            case "FLOAT":
                return new com.xxdb.data.BasicFloatVector(rows);
            case "DOUBLE":
                return new com.xxdb.data.BasicDoubleVector(rows);
            case "DATE":
                return new com.xxdb.data.BasicDateVector(rows);
            case "TIME":
                return new com.xxdb.data.BasicTimeVector(rows);
            case "TIMESTAMP":
                return new com.xxdb.data.BasicTimestampVector(rows);
            case "STRING":
                return new com.xxdb.data.BasicStringVector(rows);
            default:
                // Fallback: keep as ANY to avoid NPE, but may still fail server-side.
                return new com.xxdb.data.BasicAnyVector(rows);
        }
    }

    private int resolveSingleKeyWriteIndex() {
        List<String> keys = pluginConfig.get(KEY_COL_NAMES);
        if (keys == null || keys.isEmpty()) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR,
                    "Legacy upsert requires exactly 1 keyColNames, but it is not configured.");
        }
        if (keys.size() != 1) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR,
                    "Legacy upsert supports only 1 keyColNames on DolphinDB server < 2.00.8. "
                            + "Current keyColNames="
                            + keys);
        }
        String key = MultithreadedTableWriterFactory.normalizeColumnName(keys.get(0));
        if (key == null || key.trim().isEmpty()) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR, "Legacy upsert keyColNames is blank.");
        }
        if (dolphinDBColumnNames != null) {
            for (int i = 0; i < dolphinDBColumnNames.length; i++) {
                if (dolphinDBColumnNames[i] != null
                        && dolphinDBColumnNames[i].equalsIgnoreCase(key)) {
                    return i;
                }
            }
        }
        throw new DolphinDBConnectorException(
                DolphinDBErrorCode.WRITE_DATA_ERROR,
                "Legacy upsert key column '"
                        + key
                        + "' not found in DolphinDB columns: "
                        + joinWithLimit(dolphinDBColumnNames, 50));
    }

    private String createLegacyUpsertFunction(DolphinDBServerInfo serverInfo, String keyColumnName)
            throws IOException {
        String fn = "st_legacy_upsert_" + Long.toUnsignedString(System.nanoTime());
        String database = escapeDolphinDbString(serverInfo.database);
        String table = escapeDolphinDbString(serverInfo.table);
        String key = escapeDolphinDbIdentifier(keyColumnName);
        String def =
                "def "
                        + fn
                        + "(tb){ "
                        + "t=loadTable(\""
                        + database
                        + "\",\""
                        + table
                        + "\"); "
                        + "delete from t where "
                        + key
                        + " in tb."
                        + key
                        + "; "
                        + "tableInsert(t, tb); "
                        + "}";
        legacyConnection.run(def);
        return fn;
    }

    private static String escapeDolphinDbString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeDolphinDbIdentifier(String s) {
        if (s == null) {
            return "";
        }
        // For now assume column identifiers are safe after normalizeColumnName.
        return MultithreadedTableWriterFactory.normalizeColumnName(s);
    }

    private static DolphinDBServerInfo parseServerInfo(ReadonlyConfig pluginConfig) {
        List<String> addresses = pluginConfig.get(ADDRESS);
        if (addresses == null || addresses.isEmpty()) {
            throw new DolphinDBConnectorException(
                    DolphinDBErrorCode.WRITE_DATA_ERROR, "DolphinDB address is not configured.");
        }
        String address = addresses.get(0);
        String host = address.substring(0, address.lastIndexOf(":"));
        int port = Integer.parseInt(address.substring(address.lastIndexOf(":") + 1));
        DolphinDBServerInfo info = new DolphinDBServerInfo();
        info.host = host;
        info.port = port;
        info.user = pluginConfig.get(USER);
        info.password = pluginConfig.get(PASSWORD);
        info.useSSL = pluginConfig.get(USE_SSL);
        info.database = pluginConfig.get(DATABASE);
        info.table = pluginConfig.get(TABLE);
        return info;
    }

    private static String fetchDolphinDBVersion(DolphinDBServerInfo serverInfo) {
        DBConnection conn = new DBConnection(false, serverInfo.useSSL);
        try {
            if (!conn.connect(
                    serverInfo.host, serverInfo.port, serverInfo.user, serverInfo.password)) {
                return "";
            }
            Object v = conn.run("version()");
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return "";
        } finally {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }

    static boolean shouldUseLegacyUpsertForKeyedUpsert(String versionString) {
        // DolphinDB 2.00.7 does not support upsert!{table,,`key} signature used by MTW M_Upsert.
        // Use a conservative cutoff: < 2.00.8 -> legacy delete+tableInsert.
        if (versionString == null) {
            return true;
        }
        String s = versionString.trim();
        if (s.isEmpty()) {
            return true;
        }
        String[] parts = s.split("\\s+");
        String ver = parts.length > 0 ? parts[0] : s;
        int[] v = parseVersionTriple(ver);
        int[] cutoff = new int[] {2, 0, 8};
        if (v == null) {
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (v[i] < cutoff[i]) {
                return true;
            }
            if (v[i] > cutoff[i]) {
                return false;
            }
        }
        return false;
    }

    private static int[] parseVersionTriple(String ver) {
        if (ver == null) {
            return null;
        }
        String[] segs = ver.split("\\.");
        if (segs.length < 3) {
            return null;
        }
        try {
            return new int[] {
                Integer.parseInt(segs[0]), Integer.parseInt(segs[1]), Integer.parseInt(segs[2])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class DolphinDBServerInfo {
        private String host;
        private int port;
        private String user;
        private String password;
        private boolean useSSL;
        private String database;
        private String table;
    }

    private void validateBoolColumns(Object[] rawFields, Object[] finalFields) {
        if (dolphinDBColumnTypes == null || dolphinDBColumnTypes.length == 0) {
            return;
        }
        int len = Math.min(finalFields.length, dolphinDBColumnTypes.length);
        for (int writeIndex = 0; writeIndex < len; writeIndex++) {
            if (!isDolphinBoolType(dolphinDBColumnTypes[writeIndex])) {
                continue;
            }
            Object value = finalFields[writeIndex];
            if (value == null || value instanceof Boolean || value instanceof BasicBoolean) {
                continue;
            }
            int rowIndex = writeIndex;
            if (dolphinDBColumnToRowIndex != null
                    && writeIndex >= 0
                    && writeIndex < dolphinDBColumnToRowIndex.length) {
                rowIndex = dolphinDBColumnToRowIndex[writeIndex];
            }
            Object raw =
                    rowIndex >= 0 && rawFields != null && rowIndex < rawFields.length
                            ? rawFields[rowIndex]
                            : null;
            String columnName = getColumnNameForDebug(writeIndex);
            String message =
                    "Pre-validation failed for DolphinDB BOOL column '"
                            + columnName
                            + "': raw="
                            + raw
                            + " rawClass="
                            + (raw == null ? "null" : raw.getClass().getName())
                            + " coerced="
                            + value
                            + " coercedClass="
                            + value.getClass().getName()
                            + " dolphinType="
                            + dolphinDBColumnTypes[writeIndex]
                            + " rowIndex="
                            + rowIndex
                            + " writeIndex="
                            + writeIndex;
            log.error(message);
            throw new DolphinDBConnectorException(DolphinDBErrorCode.WRITE_DATA_ERROR, message);
        }
    }

    private Object[] buildWriteFields(Object[] fields) {
        if (dolphinDBColumnToRowIndex == null || dolphinDBColumnToRowIndex.length == 0) {
            Object[] out = new Object[fields.length];
            for (int i = 0; i < out.length; i++) {
                SeaTunnelDataType<?> fieldType = seaTunnelRowType.getFieldType(i);
                Object coerced =
                        coerceFieldValue(fieldType, seaTunnelRowType.getFieldName(i), fields[i]);
                if (dolphinDBColumnTypes != null && i < dolphinDBColumnTypes.length) {
                    coerced = coerceFieldValueByDolphinType(dolphinDBColumnTypes[i], coerced);
                }
                out[i] = coerced;
            }
            return out;
        }
        Object[] out = new Object[dolphinDBColumnToRowIndex.length];
        for (int i = 0; i < out.length; i++) {
            int rowIndex = dolphinDBColumnToRowIndex[i];
            if (rowIndex < 0 || rowIndex >= fields.length) {
                Object coerced = null;
                if (dolphinDBColumnTypes != null && i < dolphinDBColumnTypes.length) {
                    coerced = coerceFieldValueByDolphinType(dolphinDBColumnTypes[i], coerced);
                }
                out[i] = coerced;
                continue;
            }
            SeaTunnelDataType<?> fieldType = seaTunnelRowType.getFieldType(rowIndex);
            String fieldName = seaTunnelRowType.getFieldName(rowIndex);
            Object coerced = coerceFieldValue(fieldType, fieldName, fields[rowIndex]);
            if (dolphinDBColumnTypes != null && i < dolphinDBColumnTypes.length) {
                coerced = coerceFieldValueByDolphinType(dolphinDBColumnTypes[i], coerced);
            }
            out[i] = coerced;
        }
        return out;
    }

    private String getColumnNameForDebug(int writeIndex) {
        if (dolphinDBColumnNames != null
                && writeIndex >= 0
                && writeIndex < dolphinDBColumnNames.length) {
            return dolphinDBColumnNames[writeIndex];
        }
        if (writeIndex >= 0 && writeIndex < seaTunnelRowType.getTotalFields()) {
            return seaTunnelRowType.getFieldName(writeIndex);
        }
        return "unknown";
    }

    private SqlType getColumnSqlTypeForDebug(int writeIndex) {
        int rowIndex = writeIndex;
        if (dolphinDBColumnToRowIndex != null
                && writeIndex >= 0
                && writeIndex < dolphinDBColumnToRowIndex.length) {
            rowIndex = dolphinDBColumnToRowIndex[writeIndex];
        }
        if (rowIndex >= 0 && rowIndex < seaTunnelRowType.getTotalFields()) {
            return seaTunnelRowType.getFieldType(rowIndex).getSqlType();
        }
        return SqlType.STRING;
    }

    private DolphinDBColumnMapping resolveDolphinDBColumnMapping() {
        List<String> addresses = pluginConfig.get(ADDRESS);
        if (addresses == null || addresses.isEmpty()) {
            return DolphinDBColumnMapping.empty();
        }
        String address = addresses.get(0);
        String host = address.substring(0, address.lastIndexOf(":"));
        int port = Integer.parseInt(address.substring(address.lastIndexOf(":") + 1));
        String database = pluginConfig.get(DATABASE);
        String table = pluginConfig.get(TABLE);
        boolean useSSL = pluginConfig.get(USE_SSL);
        DBConnection conn = new DBConnection(false, useSSL);
        try {
            if (!conn.connect(host, port, pluginConfig.get(USER), pluginConfig.get(PASSWORD))) {
                log.warn(
                        "Failed to connect to DolphinDB for schema mapping, fallback to row order.");
                return DolphinDBColumnMapping.empty();
            }
            BasicTable colDefs =
                    (BasicTable)
                            conn.run(
                                    String.format(
                                            "loadTable(\"%s\",\"%s\").schema().colDefs",
                                            database, table));
            int rowCount = colDefs.rows();
            int nameColIndex =
                    findColumnIndexByName(colDefs, "name", "colName", "columnName", "col");
            if (nameColIndex < 0) {
                nameColIndex = 0;
            }
            int typeColIndex = findBestTypeColumnIndex(colDefs, nameColIndex);
            Vector nameCol = colDefs.getColumn(nameColIndex);
            Vector typeCol =
                    typeColIndex >= 0 && typeColIndex < colDefs.columns()
                            ? colDefs.getColumn(typeColIndex)
                            : null;
            String[] columnNames = new String[rowCount];
            String[] columnTypes = new String[rowCount];
            for (int i = 0; i < rowCount; i++) {
                columnNames[i] = nameCol.get(i).getString();
                columnTypes[i] = normalizeDolphinType(typeCol == null ? null : typeCol.get(i));
            }
            int[] columnToRowIndex = new int[rowCount];
            String[] rowFieldNames = seaTunnelRowType.getFieldNames();
            for (int i = 0; i < rowCount; i++) {
                columnToRowIndex[i] = findFieldIndex(rowFieldNames, columnNames[i]);
            }
            boolean identityMapping = rowCount == rowFieldNames.length;
            for (int i = 0; identityMapping && i < rowCount; i++) {
                if (columnToRowIndex[i] != i) {
                    identityMapping = false;
                }
            }
            boolean hasMismatch = false;
            for (int idx : columnToRowIndex) {
                if (idx < 0) {
                    hasMismatch = true;
                    break;
                }
            }
            log.info(
                    "DolphinDB schema mapping resolved. table={}, identityMapping={}, hasMismatch={}, dolphinColumns={}, rowFields={}, dolphinTypes={}",
                    table,
                    identityMapping,
                    hasMismatch,
                    joinWithLimit(columnNames, 200),
                    joinWithLimit(rowFieldNames, 200),
                    joinWithLimit(columnTypes, 200));
            return new DolphinDBColumnMapping(columnNames, columnToRowIndex, columnTypes);
        } catch (Exception e) {
            log.warn("Failed to resolve DolphinDB schema mapping, fallback to row order.", e);
            return DolphinDBColumnMapping.empty();
        } finally {
            conn.close();
        }
    }

    private static int findFieldIndex(String[] fieldNames, String name) {
        String normalized = normalizeColumnNameOrEmpty(name);
        for (int i = 0; i < fieldNames.length; i++) {
            if (normalizeColumnNameOrEmpty(fieldNames[i]).equals(normalized)) {
                return i;
            }
        }
        for (int i = 0; i < fieldNames.length; i++) {
            if (normalizeColumnNameOrEmpty(fieldNames[i]).equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeColumnNameOrEmpty(String columnName) {
        String normalized = MultithreadedTableWriterFactory.normalizeColumnName(columnName);
        return normalized == null ? "" : normalized;
    }

    static Object coerceFieldValue(SeaTunnelDataType<?> fieldType, String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        SqlType sqlType = fieldType.getSqlType();
        if (sqlType.equals(SqlType.DECIMAL)) {
            // dolphinDB support decimal after 2.00.8
            if (value instanceof BigDecimal) {
                return ((BigDecimal) value).doubleValue();
            }
            return value;
        }
        if (sqlType.equals(SqlType.BOOLEAN)) {
            return coerceToBoolean(value);
        }
        if (looksLikeBooleanFieldName(fieldName)) {
            Object coerced = coerceToBoolean(value);
            if (coerced instanceof Boolean || coerced == null) {
                return coerced;
            }
        }
        return value;
    }

    static Object coerceFieldValueByDolphinType(String dolphinType, Object value) {
        if (isDolphinBoolType(dolphinType)) {
            return coerceToDolphinBoolean(value);
        }
        return value;
    }

    private static boolean isDolphinBoolType(String dolphinType) {
        if (dolphinType == null) {
            return false;
        }
        String t = dolphinType.trim().toUpperCase();
        return "BOOL".equals(t) || "BOOLEAN".equals(t);
    }

    private static Object coerceToDolphinBoolean(Object value) {
        if (value == null) {
            return dolphinBooleanNull();
        }
        if (value instanceof BasicBoolean) {
            return value;
        }
        Object coerced = coerceToBoolean(value);
        if (coerced == null) {
            return dolphinBooleanNull();
        }
        if (coerced instanceof Boolean) {
            return new BasicBoolean((Boolean) coerced);
        }
        // Keep original; validateBoolColumns will fail-fast with details.
        return coerced;
    }

    private static BasicBoolean dolphinBooleanNull() {
        BasicBoolean b = new BasicBoolean(false);
        b.setNull();
        return b;
    }

    private static int findColumnIndexByName(BasicTable table, String... candidates) {
        if (table == null || candidates == null || candidates.length == 0) {
            return -1;
        }
        for (int i = 0; i < table.columns(); i++) {
            String colName = table.getColumnName(i);
            if (colName == null) {
                continue;
            }
            for (String candidate : candidates) {
                if (candidate != null && colName.equalsIgnoreCase(candidate)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findBestTypeColumnIndex(BasicTable table, int nameColIndex) {
        if (table == null || table.columns() <= 1 || table.rows() <= 0) {
            return -1;
        }
        int idx =
                findColumnIndexByName(
                        table, "typeString", "typeStr", "typeName", "dataType", "type");
        if (idx >= 0 && idx != nameColIndex) {
            String sample = normalizeDolphinType(table.getColumn(idx).get(0));
            if (isLikelyDolphinTypeString(sample)) {
                return idx;
            }
        }
        for (int i = 0; i < table.columns(); i++) {
            if (i == nameColIndex) {
                continue;
            }
            String colName = table.getColumnName(i);
            if (colName == null) {
                continue;
            }
            String lower = colName.toLowerCase();
            if (!lower.contains("type")) {
                continue;
            }
            String sample = normalizeDolphinType(table.getColumn(i).get(0));
            if (isLikelyDolphinTypeString(sample)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLikelyDolphinTypeString(String normalizedType) {
        if (normalizedType == null) {
            return false;
        }
        switch (normalizedType) {
            case "BOOL":
            case "BOOLEAN":
            case "CHAR":
            case "SHORT":
            case "INT":
            case "LONG":
            case "FLOAT":
            case "DOUBLE":
            case "DATE":
            case "TIME":
            case "TIMESTAMP":
            case "MINUTE":
            case "SECOND":
            case "DATETIME":
            case "NANOTIME":
            case "NANOTIMESTAMP":
            case "STRING":
            case "SYMBOL":
            case "BLOB":
            case "UUID":
            case "IPADDR":
            case "INT128":
            case "DECIMAL32":
            case "DECIMAL64":
            case "DECIMAL128":
                return true;
            default:
                return false;
        }
    }

    private static String normalizeDolphinType(Object typeCell) {
        if (typeCell == null) {
            return null;
        }
        String s = String.valueOf(typeCell).trim();
        if (s.isEmpty()) {
            return null;
        }
        // DolphinDB schema colDefs may include quotes or backticks in some environments
        return normalizeColumnNameOrEmpty(s).toUpperCase();
    }

    private static boolean looksLikeBooleanFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String name = fieldName.trim().toLowerCase();
        return name.contains("boolean") || name.endsWith("_bool") || name.endsWith("_flag");
    }

    private static Object coerceToBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            if (bytes.length == 0) {
                return null;
            }
            if (bytes.length == 1) {
                return bytes[0] != 0;
            }
            return value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                return null;
            }
            if ("1".equals(s)
                    || "true".equalsIgnoreCase(s)
                    || "t".equalsIgnoreCase(s)
                    || "y".equalsIgnoreCase(s)
                    || "yes".equalsIgnoreCase(s)
                    || "on".equalsIgnoreCase(s)
                    || "enabled".equalsIgnoreCase(s)) {
                return true;
            }
            if ("0".equals(s)
                    || "false".equalsIgnoreCase(s)
                    || "f".equalsIgnoreCase(s)
                    || "n".equalsIgnoreCase(s)
                    || "no".equalsIgnoreCase(s)
                    || "off".equalsIgnoreCase(s)
                    || "disabled".equalsIgnoreCase(s)) {
                return false;
            }
            try {
                return Integer.parseInt(s) != 0;
            } catch (NumberFormatException ignored) {
                Boolean base64Bool = tryDecodeBase64Boolean(s);
                if (base64Bool != null) {
                    return base64Bool;
                }
                return value;
            }
        }
        return value;
    }

    /**
     * Debezium may represent MySQL BIT(1) as a base64-encoded single byte string (e.g. "AA==" or
     * "AQ=="). If so, decode and map 0/1 to false/true.
     */
    private static Boolean tryDecodeBase64Boolean(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        if (s.length() < 4) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(s);
            if (decoded.length == 1) {
                return decoded[0] != 0;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(s);
            if (decoded.length == 1) {
                return decoded[0] != 0;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64
        }
        return null;
    }

    private static final class DolphinDBColumnMapping {
        private final String[] columnNames;
        private final int[] columnToRowIndex;
        private final String[] columnTypes;

        private DolphinDBColumnMapping(
                String[] columnNames, int[] columnToRowIndex, String[] columnTypes) {
            this.columnNames = columnNames;
            this.columnToRowIndex = columnToRowIndex;
            this.columnTypes = columnTypes;
        }

        private static DolphinDBColumnMapping empty() {
            return new DolphinDBColumnMapping(null, null, null);
        }
    }

    private static String joinWithLimit(String[] values, int maxItems) {
        if (values == null) {
            return "null";
        }
        if (maxItems <= 0 || values.length <= maxItems) {
            return Arrays.toString(values);
        }
        String[] head = Arrays.copyOf(values, maxItems);
        return Arrays.toString(head) + "...(total=" + values.length + ")";
    }
}
