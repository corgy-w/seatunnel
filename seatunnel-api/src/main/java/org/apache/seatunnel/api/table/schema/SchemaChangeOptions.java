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

package org.apache.seatunnel.api.table.schema;

import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.SingleChoiceOption;

import java.util.Arrays;

public class SchemaChangeOptions {
    public static final String DDL_ADD_COLUMN_KEY = "ddl.add.column";
    public static final String DDL_DROP_COLUMN_KEY = "ddl.drop.column";
    public static final String DDL_RENAME_COLUMN_KEY = "ddl.rename.column";
    public static final String DDL_UPDATE_COLUMN_KEY = "ddl.update.column";

    public static final SingleChoiceOption<SchemaChangeStrategy> DDL_ADD_COLUMN =
            Options.key(DDL_ADD_COLUMN_KEY)
                    .singleChoice(
                            SchemaChangeStrategy.class,
                            Arrays.asList(
                                    SchemaChangeStrategy.IGNORE,
                                    SchemaChangeStrategy.PAUSE,
                                    SchemaChangeStrategy.APPLY))
                    .defaultValue(SchemaChangeStrategy.APPLY)
                    .withDescription("Optional schema change mode for adding column");

    public static final SingleChoiceOption<SchemaChangeStrategy> DDL_DROP_COLUMN =
            Options.key(DDL_DROP_COLUMN_KEY)
                    .singleChoice(
                            SchemaChangeStrategy.class,
                            Arrays.asList(
                                    SchemaChangeStrategy.IGNORE,
                                    SchemaChangeStrategy.PAUSE,
                                    SchemaChangeStrategy.APPLY))
                    .defaultValue(SchemaChangeStrategy.IGNORE)
                    .withDescription("Optional schema change mode for dropping column");

    public static final SingleChoiceOption<SchemaChangeStrategy> DDL_UPDATE_COLUMN =
            Options.key(DDL_UPDATE_COLUMN_KEY)
                    .singleChoice(
                            SchemaChangeStrategy.class,
                            Arrays.asList(
                                    SchemaChangeStrategy.IGNORE,
                                    SchemaChangeStrategy.PAUSE,
                                    SchemaChangeStrategy.APPLY))
                    .defaultValue(SchemaChangeStrategy.IGNORE)
                    .withDescription("Optional schema change mode for updating column");

    public static final SingleChoiceOption<SchemaChangeStrategy> DDL_RENAME_COLUMN =
            Options.key(DDL_RENAME_COLUMN_KEY)
                    .singleChoice(
                            SchemaChangeStrategy.class,
                            Arrays.asList(SchemaChangeStrategy.PAUSE, SchemaChangeStrategy.APPLY))
                    .defaultValue(SchemaChangeStrategy.APPLY)
                    .withDescription("Optional schema change mode for renaming column");
}
