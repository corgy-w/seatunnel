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

package org.apache.seatunnel.connectors.seatunnel.sapbw.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;

import java.util.List;

/**
 * SAP BW Source Configuration Options.
 *
 * <p>category = "MY_CATALOG":
 *
 * <ul>
 *   <li>Meaning: The name of the InfoProvider collection or catalog.
 *   <li>Corresponding SAP Concept: Usually corresponds to InfoArea in SAP BW or CATALOG_NAME in
 *       ODBO interface.
 * </ul>
 *
 * <p>query = "MY_CUBE":
 *
 * <ul>
 *   <li>Meaning: The name of the specific data source object.
 *   <li>Corresponding SAP Concept: Can be an InfoCube (e.g., 0D_DECU) or a BEx Query (usually
 *       starting with a letter). This is the FROM [MY_CUBE] part in MDX query.
 * </ul>
 *
 * <p>dimensions_and_measures = ["Product", "Year", "Amount"]:
 *
 * <ul>
 *   <li>Meaning: The list of fields you want to query.
 *   <li>Corresponding SAP Concepts:
 *       <ul>
 *         <li>Dimensions: Such as Product, Year. Corresponds to ON ROWS in MDX query.
 *         <li>Measures: Such as Amount, Quantity. Corresponds to ON COLUMNS in MDX query.
 *       </ul>
 *   <li>Note: The values here must be the internal technical names in SAP BW, such as 0MATERIAL
 *       instead of "Material".
 * </ul>
 *
 * <p>variables = "VAR_YEAR=2023":
 *
 * <ul>
 *   <li>Meaning: Query variables (optional).
 *   <li>Purpose: Used to filter data. For example, query only data from 2023.
 *   <li>Implementation: SeaTunnel will append these variables to the MDX query statement as SAP
 *       VARIABLES part and send to the server.
 * </ul>
 */
public class SAPBWSourceOption extends SAPCommonOption {
    public static final Option<String> CATEGORY =
            Options.key("category")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("SAP Business Warehouse category");
    public static final Option<String> QUERY =
            Options.key("query")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("SAP Business Warehouse query");
    public static final Option<List<String>> DIMENSIONS_AND_MEASURES =
            Options.key("dimensions_and_measures")
                    .listType()
                    .noDefaultValue()
                    .withDescription("SAP Business Warehouse dimensions and measures of the query");
    public static final Option<String> VARIABLES =
            Options.key("variables")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("SAP Business Warehouse variables of the query");

    public static final Option<List<QueryTableConfig>> TABLE_LIST =
            Options.key("table_list")
                    .listType(QueryTableConfig.class)
                    .noDefaultValue()
                    .withDescription("table list config");
}
