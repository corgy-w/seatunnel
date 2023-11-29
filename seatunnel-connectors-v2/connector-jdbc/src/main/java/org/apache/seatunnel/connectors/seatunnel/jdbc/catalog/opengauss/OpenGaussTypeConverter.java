/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.opengauss;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.common.exception.CommonError;
import org.apache.seatunnel.common.exception.SeaTunnelRuntimeException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.psql.PostgresTypeConverter;

import com.google.auto.service.AutoService;

@AutoService(TypeConverter.class)
public class OpenGaussTypeConverter extends PostgresTypeConverter {
    public static final OpenGaussTypeConverter INSTANCE = new OpenGaussTypeConverter();

    @Override
    public String identifier() {
        return DatabaseIdentifier.OPENGAUSS;
    }

    @Override
    public Column convert(BasicTypeDefine typeDefine) {
        try {
            return super.convert(typeDefine);
        } catch (SeaTunnelRuntimeException e) {
            throw CommonError.convertToSeaTunnelTypeError(
                    DatabaseIdentifier.OPENGAUSS, typeDefine.getDataType(), typeDefine.getName());
        }
    }

    @Override
    public BasicTypeDefine reconvert(Column column) {
        try {
            return super.reconvert(column);
        } catch (SeaTunnelRuntimeException e) {
            throw CommonError.convertToConnectorTypeError(
                    DatabaseIdentifier.OPENGAUSS,
                    column.getDataType().getSqlType().name(),
                    column.getName());
        }
    }
}
