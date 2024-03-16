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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.dws;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.psql.PostgresTypeConverter;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

// reference https://support.huaweicloud.com/sqlreference-dws/dws_06_0009.html
@Slf4j
@AutoService(TypeConverter.class)
public class DwsTypeConverter extends PostgresTypeConverter {

    // DWS jdbc driver maps several alias to real type, we use real type rather than alias:

    // number type
    public static final String DWS_TINYINT = "int1";
    public static final String DWS_TINYINT_ARRAY = "_int1";

    // varchar type
    public static final String DWS_NVARCHAR2 = "nvarchar2";
    public static final String DWS_NVARCHAR = "nvarchar";
    public static final String DWS_NVARCHAR_ARRAY = "_nvarchar";
    public static final String DWS_NVARCHAR2_ARRAY = "_nvarchar2";

    // date type

    // Date and time without time zone. Accurate to the minute, the second bit is greater than or
    // equal to 30 seconds.
    public static final String DWS_SMALLDATETIME = "smalldatetime";

    // jsonb
    public static final String DWS_JSONB = "JSONB";

    public static final DwsTypeConverter INSTANCE = new DwsTypeConverter();

    @Override
    public String identifier() {
        return DatabaseIdentifier.DWS;
    }

    @Override
    public Column convert(BasicTypeDefine typeDefine) {
        PhysicalColumn.PhysicalColumnBuilder builder =
                PhysicalColumn.builder()
                        .name(typeDefine.getName())
                        .sourceType(typeDefine.getColumnType())
                        .nullable(typeDefine.isNullable())
                        .defaultValue(typeDefine.getDefaultValue())
                        .comment(typeDefine.getComment());

        String dwsDataType = typeDefine.getDataType().toLowerCase(Locale.ROOT);
        switch (dwsDataType) {
            case DWS_TINYINT:
                // The tinyint is 0 ~ 255 in dws, so need use short to storage it.
                builder.dataType(BasicType.SHORT_TYPE);
                break;
            case DWS_TINYINT_ARRAY:
                builder.dataType(ArrayType.SHORT_ARRAY_TYPE);
                break;
            case DWS_NVARCHAR2:
            case DWS_NVARCHAR:
                if (typeDefine.getLength() != null && typeDefine.getLength() > 0) {
                    builder.columnLength(typeDefine.getLength() * 3);
                }
                builder.dataType(BasicType.STRING_TYPE);
                break;
            case DWS_NVARCHAR2_ARRAY:
            case DWS_NVARCHAR_ARRAY:
                builder.dataType(ArrayType.STRING_ARRAY_TYPE);
                break;
            case DWS_SMALLDATETIME:
                builder.dataType(LocalTimeType.LOCAL_DATE_TIME_TYPE);
                builder.scale(0);
                break;
            case DWS_JSONB:
                builder.dataType(BasicType.STRING_TYPE);
                break;
            default:
                return super.convert(typeDefine);
        }
        return builder.build();
    }

    @Override
    public BasicTypeDefine reconvert(Column column) {
        return null;
    }
}
