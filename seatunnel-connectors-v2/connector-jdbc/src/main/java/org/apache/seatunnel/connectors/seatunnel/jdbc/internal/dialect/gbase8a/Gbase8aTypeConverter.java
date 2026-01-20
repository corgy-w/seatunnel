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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a;

import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.mysql.MySqlTypeConverter;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

/**
 * Gbase8a TypeConverter. Gbase8a SQL syntax is compatible with MySQL, so we extend
 * MySqlTypeConverter.
 */
@Slf4j
@AutoService(TypeConverter.class)
public class Gbase8aTypeConverter extends MySqlTypeConverter {

    public static final Gbase8aTypeConverter INSTANCE = new Gbase8aTypeConverter();

    public Gbase8aTypeConverter() {
        super();
    }

    @Override
    public String identifier() {
        return DatabaseIdentifier.GBASE_8A;
    }
}
