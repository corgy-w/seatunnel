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

package org.apache.seatunnel.connectors.dws.guassdb.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.util.OptionRule;

import java.util.HashMap;
import java.util.Map;

public interface BaseDwsGaussDBOption {

    Option<String> DATABASE =
            Options.key("database").stringType().noDefaultValue().withDescription("database");

    Option<String> DATABASE_SCHEMA =
            Options.key("database_schema")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("database_schema");

    Option<String> TABLE =
            Options.key("table").stringType().noDefaultValue().withDescription("table");

    Option<String> URL =
            Options.key("url")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("jdbc url, eg:" + "jdbc:gaussdb://localhost:8000/postgres");

    Option<String> USER =
            Options.key("user").stringType().noDefaultValue().withDescription("jdbc user");

    Option<String> PASSWORD =
            Options.key("password").stringType().noDefaultValue().withDescription("jdbc password");

    Option<String> DRIVER =
            Options.key("driver")
                    .stringType()
                    .defaultValue("com.huawei.gauss200.jdbc.Driver")
                    .withDescription("driver");

    Option<Map<String, String>> PROPERTIES =
            Options.key("properties")
                    .mapType()
                    .defaultValue(new HashMap<>())
                    .withDescription("jdbc properties, eg: " + "{\n" + "ssl=true\n" + "}");

    Option<String> TABLE_PREFIX =
            Options.key("tablePrefix")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The table prefix name added when the table is automatically created");

    Option<String> TABLE_SUFFIX =
            Options.key("tableSuffix")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "The table suffix name added when the table is automatically created");

    Option<String> PRIMARY_KEY =
            Options.key("primary_key").stringType().noDefaultValue().withDescription("primary key");

    OptionRule getOptionRule();
}
