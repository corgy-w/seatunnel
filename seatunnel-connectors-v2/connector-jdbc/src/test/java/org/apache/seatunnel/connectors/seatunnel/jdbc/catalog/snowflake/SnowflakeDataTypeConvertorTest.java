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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.snowflake;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for SnowflakeDataTypeConvertor to verify consistent timestamp type handling
 * and proper data type conversions after fixes.
 */
public class SnowflakeDataTypeConvertorTest {

    private final SnowflakeDataTypeConvertor convertor = new SnowflakeDataTypeConvertor();

    @Test
    public void testGetIdentity() {
        assertEquals(DatabaseIdentifier.SNOWFLAKE, convertor.getIdentity());
    }

    @Test
    public void testTimestampTypesConsistency() {
        // Test all timestamp variations with correct underscores (TIMESTAMP_*)
        assertEquals(LocalTimeType.LOCAL_DATE_TIME_TYPE, convertor.toSeaTunnelType("TIMESTAMP_LTZ"));
        assertEquals(LocalTimeType.LOCAL_DATE_TIME_TYPE, convertor.toSeaTunnelType("TIMESTAMP_NTZ"));
        assertEquals(LocalTimeType.LOCAL_DATE_TIME_TYPE, convertor.toSeaTunnelType("TIMESTAMP_TZ"));
        assertEquals(LocalTimeType.LOCAL_DATE_TIME_TYPE, convertor.toSeaTunnelType("TIMESTAMP"));
        assertEquals(LocalTimeType.LOCAL_DATE_TIME_TYPE, convertor.toSeaTunnelType("DATE_TIME"));
    }

    @Test
    public void testGeographyAndGeometryMapping() {
        // Test GEOGRAPHY maps to byte array (as per SnowflakeDataTypeConvertor)
        assertEquals(PrimitiveByteArrayType.INSTANCE, convertor.toSeaTunnelType("GEOGRAPHY"));
        
        // Test GEOMETRY maps to String (as per SnowflakeDataTypeConvertor)
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("GEOMETRY"));
    }

    @Test 
    public void testDecimalTypeWithPrecisionScale() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("precision", 15);
        properties.put("scale", 2);
        
        DecimalType result = (DecimalType) convertor.toSeaTunnelType("DECIMAL", properties);
        assertEquals(15, result.getPrecision());
        assertEquals(2, result.getScale());
        
        // Test default values
        DecimalType defaultResult = (DecimalType) convertor.toSeaTunnelType("NUMBER");
        assertEquals(10, defaultResult.getPrecision());
        assertEquals(0, defaultResult.getScale());
    }

    @Test
    public void testIntegerTypes() {
        assertEquals(BasicType.SHORT_TYPE, convertor.toSeaTunnelType("SMALLINT"));
        assertEquals(BasicType.SHORT_TYPE, convertor.toSeaTunnelType("TINYINT"));
        assertEquals(BasicType.SHORT_TYPE, convertor.toSeaTunnelType("BYTEINT"));
        assertEquals(BasicType.INT_TYPE, convertor.toSeaTunnelType("INTEGER"));
        assertEquals(BasicType.INT_TYPE, convertor.toSeaTunnelType("INT"));
        assertEquals(BasicType.LONG_TYPE, convertor.toSeaTunnelType("BIGINT"));
    }

    @Test
    public void testFloatingPointTypes() {
        assertEquals(BasicType.FLOAT_TYPE, convertor.toSeaTunnelType("REAL"));
        assertEquals(BasicType.FLOAT_TYPE, convertor.toSeaTunnelType("FLOAT4"));
        assertEquals(BasicType.DOUBLE_TYPE, convertor.toSeaTunnelType("DOUBLE"));
        assertEquals(BasicType.DOUBLE_TYPE, convertor.toSeaTunnelType("DOUBLE PRECISION"));
        assertEquals(BasicType.DOUBLE_TYPE, convertor.toSeaTunnelType("FLOAT8"));
        assertEquals(BasicType.DOUBLE_TYPE, convertor.toSeaTunnelType("FLOAT"));
    }

    @Test
    public void testStringTypes() {
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("CHAR"));
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("CHARACTER"));
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("VARCHAR"));
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("STRING"));
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("TEXT"));
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("VARIANT"));
        assertEquals(BasicType.STRING_TYPE, convertor.toSeaTunnelType("OBJECT"));
    }

    @Test
    public void testBinaryTypes() {
        assertEquals(PrimitiveByteArrayType.INSTANCE, convertor.toSeaTunnelType("BINARY"));
        assertEquals(PrimitiveByteArrayType.INSTANCE, convertor.toSeaTunnelType("VARBINARY"));
    }

    @Test
    public void testDateTimeTypes() {
        assertEquals(LocalTimeType.LOCAL_DATE_TYPE, convertor.toSeaTunnelType("DATE"));
        assertEquals(LocalTimeType.LOCAL_TIME_TYPE, convertor.toSeaTunnelType("TIME"));
    }

    @Test
    public void testBooleanType() {
        assertEquals(BasicType.BOOLEAN_TYPE, convertor.toSeaTunnelType("BOOLEAN"));
    }

    @Test
    public void testReverseConversion() {
        // Test SeaTunnel types back to Snowflake types
        assertEquals("SMALLINT", convertor.toConnectorType(BasicType.SHORT_TYPE, null));
        assertEquals("INTEGER", convertor.toConnectorType(BasicType.INT_TYPE, null));
        assertEquals("BIGINT", convertor.toConnectorType(BasicType.LONG_TYPE, null));
        assertEquals("DECIMAL", convertor.toConnectorType(new DecimalType(10, 2), null));
        assertEquals("FLOAT4", convertor.toConnectorType(BasicType.FLOAT_TYPE, null));
        assertEquals("DOUBLE PRECISION", convertor.toConnectorType(BasicType.DOUBLE_TYPE, null));
        assertEquals("BOOLEAN", convertor.toConnectorType(BasicType.BOOLEAN_TYPE, null));
        assertEquals("TEXT", convertor.toConnectorType(BasicType.STRING_TYPE, null));
        assertEquals("DATE", convertor.toConnectorType(LocalTimeType.LOCAL_DATE_TYPE, null));
        assertEquals("GEOMETRY", convertor.toConnectorType(PrimitiveByteArrayType.INSTANCE, null));
        assertEquals("TIME", convertor.toConnectorType(LocalTimeType.LOCAL_TIME_TYPE, null));
        assertEquals("TIMESTAMP", convertor.toConnectorType(LocalTimeType.LOCAL_DATE_TIME_TYPE, null));
    }

    @Test
    public void testUnsupportedType() {
        assertThrows(UnsupportedOperationException.class, () -> {
            convertor.toSeaTunnelType("UNSUPPORTED_TYPE");
        });
        
        assertThrows(UnsupportedOperationException.class, () -> {
            convertor.toConnectorType(BasicType.DOUBLE_TYPE, null);
        });
    }
}