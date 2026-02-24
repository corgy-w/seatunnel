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

package org.apache.seatunnel.transform.dmleventfilter.jsonenhancer;

import org.apache.seatunnel.api.table.type.RowKind;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration holder for Custom CDC JSON enhancer.
 *
 * <p>Supports two modes:
 *
 * <ul>
 *   <li>Single-field mode: Uses dataField for all operations (legacy mode)
 *   <li>Dual-field mode: Uses beforeField/afterField like Debezium/OGG
 * </ul>
 */
public class CustomCdcConfig implements Serializable {

    private final String operationTypeField;
    private final String dataField; // Legacy: single data field (for backward compatibility)
    private final String beforeField; // New: before field (like Debezium)
    private final String afterField; // New: after field (like Debezium)
    private final Map<String, RowKind> operationTypeMapping;
    private final Map<RowKind, String> reverseMapping;

    /**
     * Legacy constructor for single-field mode (backward compatibility).
     *
     * @deprecated Use {@link #CustomCdcConfig(String, String, String, Map, Map)} for dual-field
     *     mode
     */
    @Deprecated
    public CustomCdcConfig(
            String operationTypeField,
            String dataField,
            Map<String, RowKind> operationTypeMapping,
            Map<RowKind, String> reverseMapping) {
        this.operationTypeField = operationTypeField;
        this.dataField = dataField;
        this.beforeField = null;
        this.afterField = null;
        this.operationTypeMapping =
                Collections.unmodifiableMap(new HashMap<>(operationTypeMapping));
        this.reverseMapping = Collections.unmodifiableMap(new HashMap<>(reverseMapping));
    }

    /**
     * New constructor for dual-field mode (before/after like Debezium).
     *
     * @param operationTypeField The field name for operation type (e.g., "op_type")
     * @param beforeField The field name for before data (e.g., "before")
     * @param afterField The field name for after data (e.g., "after")
     * @param operationTypeMapping Mapping from operation type string to RowKind
     * @param reverseMapping Mapping from RowKind to operation type string
     */
    public CustomCdcConfig(
            String operationTypeField,
            String beforeField,
            String afterField,
            Map<String, RowKind> operationTypeMapping,
            Map<RowKind, String> reverseMapping) {
        this.operationTypeField = operationTypeField;
        this.dataField = null;
        this.beforeField = beforeField;
        this.afterField = afterField;
        this.operationTypeMapping =
                Collections.unmodifiableMap(new HashMap<>(operationTypeMapping));
        this.reverseMapping = Collections.unmodifiableMap(new HashMap<>(reverseMapping));
    }

    public String getOperationTypeField() {
        return operationTypeField;
    }

    public String getDataField() {
        return dataField;
    }

    public String getBeforeField() {
        return beforeField;
    }

    public String getAfterField() {
        return afterField;
    }

    public Map<String, RowKind> getOperationTypeMapping() {
        return operationTypeMapping;
    }

    public Map<RowKind, String> getReverseMapping() {
        return reverseMapping;
    }

    /**
     * Check if this config is in dual-field mode (before/after).
     *
     * @return true if beforeField and afterField are configured
     */
    public boolean isDualFieldMode() {
        return beforeField != null && afterField != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CustomCdcConfig that = (CustomCdcConfig) o;
        return operationTypeField.equals(that.operationTypeField)
                && java.util.Objects.equals(dataField, that.dataField)
                && java.util.Objects.equals(beforeField, that.beforeField)
                && java.util.Objects.equals(afterField, that.afterField)
                && operationTypeMapping.equals(that.operationTypeMapping)
                && reverseMapping.equals(that.reverseMapping);
    }

    @Override
    public int hashCode() {
        int result = operationTypeField.hashCode();
        result = 31 * result + (dataField != null ? dataField.hashCode() : 0);
        result = 31 * result + (beforeField != null ? beforeField.hashCode() : 0);
        result = 31 * result + (afterField != null ? afterField.hashCode() : 0);
        result = 31 * result + operationTypeMapping.hashCode();
        result = 31 * result + reverseMapping.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CustomCdcConfig{"
                + "operationTypeField='"
                + operationTypeField
                + '\''
                + ", dataField='"
                + dataField
                + '\''
                + ", beforeField='"
                + beforeField
                + '\''
                + ", afterField='"
                + afterField
                + '\''
                + ", operationTypeMapping="
                + operationTypeMapping
                + ", reverseMapping="
                + reverseMapping
                + '}';
    }
}
