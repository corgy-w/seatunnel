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

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.transform.exception.TransformException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DMLEventFilterTransformConfigTest {

    private ReadonlyConfig cfg(Map<String, Object> m) {
        return ReadonlyConfig.fromMap(m);
    }

    @Test
    void testSoftDeleteRequiresFields() {
        Map<String, Object> m1 = new HashMap<>();
        m1.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.SOFT_DELETE.name());
        m1.put(DMLEventFilterTransformConfig.MARKER_FIELD_VALUE.key(), "Y");
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m1)));

        Map<String, Object> m2 = new HashMap<>();
        m2.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.SOFT_DELETE.name());
        m2.put(DMLEventFilterTransformConfig.MARKER_FIELD_NAME.key(), "is_deleted");
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m2)));
    }

    @Test
    void testAppendModeRequiresNames() {
        Map<String, Object> m1 = new HashMap<>();
        m1.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.APPEND_MODE.name());
        m1.put(DMLEventFilterTransformConfig.DML_MARKER_FIELD_NAME.key(), "op_type");
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m1)));

        Map<String, Object> m2 = new HashMap<>();
        m2.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.APPEND_MODE.name());
        m2.put(DMLEventFilterTransformConfig.TIMESTAMP_FIELD_NAME.key(), "op_ts");
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m2)));
    }

    @Test
    void testAddDmlMarkerNameRequirementsWhenEnabled() {
        Map<String, Object> m1 = new HashMap<>();
        m1.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.ADD_DML_MARKER.name());
        m1.put(DMLEventFilterTransformConfig.DML_MARKER_ENABLED.key(), true);
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m1)));

        Map<String, Object> m2 = new HashMap<>();
        m2.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.ADD_DML_MARKER.name());
        m2.put(DMLEventFilterTransformConfig.TIMESTAMP_ENABLED.key(), true);
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m2)));
    }

    @Test
    void testPrecisionAndMarkerLengthValidation() {
        // timestamp_precision out of range
        Map<String, Object> m1 = new HashMap<>();
        m1.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.APPEND_MODE.name());
        m1.put(DMLEventFilterTransformConfig.TIMESTAMP_FIELD_NAME.key(), "ts");
        m1.put(DMLEventFilterTransformConfig.DML_MARKER_FIELD_NAME.key(), "op");
        m1.put(DMLEventFilterTransformConfig.TIMESTAMP_PRECISION.key(), 10);
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m1)));

        // marker_field_length must > 0
        Map<String, Object> m2 = new HashMap<>();
        m2.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.ADD_DML_MARKER.name());
        m2.put(DMLEventFilterTransformConfig.DML_MARKER_ENABLED.key(), true);
        m2.put(DMLEventFilterTransformConfig.DML_MARKER_FIELD_NAME.key(), "op");
        m2.put(DMLEventFilterTransformConfig.MARKER_FIELD_LENGTH.key(), 0);
        Assertions.assertThrows(
                TransformException.class, () -> DMLEventFilterTransformConfig.of(cfg(m2)));
    }

    @Test
    void testFilterDmlHappyConfig() {
        Map<String, Object> m = new HashMap<>();
        m.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.FILTER_DML.name());
        m.put(DMLEventFilterTransformConfig.EXCLUDE_KINDS.key(), Arrays.asList(RowKind.DELETE));
        DMLEventFilterTransformConfig.of(cfg(m));
    }
}
