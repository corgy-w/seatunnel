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

package org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.source;

import org.apache.seatunnel.api.table.catalog.DataTypeConvertor;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.oracle.OracleDataTypeConvertor;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter;

import com.google.auto.service.AutoService;

import java.util.Map;

/** @deprecated instead by {@link OracleTypeConverter} */
@Deprecated
@AutoService(DataTypeConvertor.class)
public class OracleAgentDataTypeConvertor implements DataTypeConvertor<String> {
    private static final OracleDataTypeConvertor ORACLE_DATA_TYPE_CONVERTOR =
            new OracleDataTypeConvertor();

    @Override
    public SeaTunnelDataType<?> toSeaTunnelType(String field, String connectorDataType) {
        return ORACLE_DATA_TYPE_CONVERTOR.toSeaTunnelType(field, connectorDataType);
    }

    @Override
    public SeaTunnelDataType<?> toSeaTunnelType(
            String field, String connectorDataType, Map<String, Object> dataTypeProperties) {
        return ORACLE_DATA_TYPE_CONVERTOR.toSeaTunnelType(
                field, connectorDataType, dataTypeProperties);
    }

    @Override
    public String toConnectorType(
            String field,
            SeaTunnelDataType<?> seaTunnelDataType,
            Map<String, Object> dataTypeProperties) {
        return ORACLE_DATA_TYPE_CONVERTOR.toConnectorType(
                field, seaTunnelDataType, dataTypeProperties);
    }

    @Override
    public String getIdentity() {
        return "OracleAgent";
    }
}
