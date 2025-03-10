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

package org.apache.seatunnel.connectors.seatunnel.cdc.mysql.utils;

import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.connector.mysql.MySqlOffsetContext;
import io.debezium.connector.mysql.MySqlPartition;
import io.debezium.connector.mysql.MySqlValueConverters;
import io.debezium.relational.TableId;
import io.debezium.schema.SchemaChangeEvent;
import io.debezium.schema.TopicSelector;
import io.debezium.util.SchemaNameAdjuster;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CustomMySqlDatabaseSchema extends io.debezium.connector.mysql.MySqlDatabaseSchema {

    public CustomMySqlDatabaseSchema(
            MySqlConnectorConfig connectorConfig,
            MySqlValueConverters valueConverter,
            TopicSelector<TableId> topicSelector,
            SchemaNameAdjuster schemaNameAdjuster,
            boolean tableIdCaseInsensitive) {
        super(
                connectorConfig,
                valueConverter,
                topicSelector,
                schemaNameAdjuster,
                tableIdCaseInsensitive);
    }

    @Override
    public List<SchemaChangeEvent> parseStreamingDdl(
            MySqlPartition partition,
            String ddlStatements,
            String databaseName,
            MySqlOffsetContext offset,
            Instant sourceTime) {
        List<SchemaChangeEvent> events =
                super.parseStreamingDdl(partition, ddlStatements, databaseName, offset, sourceTime);
        String currentGtidSet = offset.gtidSet();
        if (currentGtidSet == null) {
            return events;
        }
        return events.stream()
                .map(
                        event -> {
                            Map<String, Object> offsetMap = (Map<String, Object>) event.getOffset();
                            Object oldGtidSet = offsetMap.get(MySqlOffsetContext.GTID_SET_KEY);
                            /**
                             * DDL does not start transactions, so the current gtid should be used
                             * directly. {@link MySqlOffsetContext#commitTransaction()}
                             */
                            offsetMap.put(MySqlOffsetContext.GTID_SET_KEY, currentGtidSet);
                            log.info(
                                    "Overwrite schema change event from restartGtidSet {} to currentGtidSet {}",
                                    oldGtidSet,
                                    currentGtidSet);

                            return event;
                        })
                .collect(Collectors.toList());
    }
}
