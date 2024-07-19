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

package org.apache.seatunnel.connectors.cdc.base.source;

import org.apache.seatunnel.api.event.EventType;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.schema.SchemaChangeEventRecognizer;
import org.apache.seatunnel.api.table.schema.SchemaChangeStrategy;
import org.apache.seatunnel.api.table.schema.SchemaChangeType;
import org.apache.seatunnel.api.table.schema.event.AlterTableColumnEvent;
import org.apache.seatunnel.api.table.schema.event.AlterTableColumnsEvent;
import org.apache.seatunnel.api.table.schema.event.AlterTableDropColumnEvent;
import org.apache.seatunnel.api.table.schema.event.SchemaChangeEvent;
import org.apache.seatunnel.connectors.cdc.debezium.DebeziumDeserializationSchema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class SchemaChangeEventStrategy
        implements Function<SchemaChangeEvent, SchemaChangeStrategy> {
    private final DebeziumDeserializationSchema schema;
    private final Map<SchemaChangeType, SchemaChangeStrategy> schemaChangeStrategys;

    @Override
    public SchemaChangeStrategy apply(SchemaChangeEvent event) {
        if (EventType.SCHEMA_CHANGE_UPDATE_COLUMNS.equals(event.getEventType())) {
            AlterTableColumnsEvent columnsEvent = (AlterTableColumnsEvent) event;
            boolean dropPrimaryKey = false;
            for (AlterTableColumnEvent columnEvent : columnsEvent.getEvents()) {
                if (columnEvent.getEventType() == EventType.SCHEMA_CHANGE_DROP_COLUMN) {
                    AlterTableDropColumnEvent dropColumnEvent =
                            (AlterTableDropColumnEvent) columnEvent;
                    if (dropPrimaryKey(dropColumnEvent)) {
                        dropColumnEvent.setPrimaryKey(true);
                        dropPrimaryKey = true;
                    }
                }
            }
            if (dropPrimaryKey) {
                log.warn(
                        "The table {} primary key column is dropped, job will be auto suspended.",
                        columnsEvent.getTablePath());
                return SchemaChangeStrategy.PAUSE;
            }
        }

        if (EventType.SCHEMA_CHANGE_DROP_COLUMN.equals(event.getEventType())) {
            AlterTableDropColumnEvent dropColumnEvent = (AlterTableDropColumnEvent) event;
            boolean dropPrimaryKey = dropPrimaryKey(dropColumnEvent);
            if (dropPrimaryKey) {
                log.warn(
                        "The table {} primary key column is dropped, job will be auto suspended.",
                        dropColumnEvent.getTablePath());
                dropColumnEvent.setPrimaryKey(true);
                return SchemaChangeStrategy.PAUSE;
            }
        }

        Optional<SchemaChangeType> optional = SchemaChangeEventRecognizer.recognize(event);
        if (!optional.isPresent()) {
            log.warn("Ignore unknown schema change event {}", event);
            return SchemaChangeStrategy.IGNORE;
        }
        return schemaChangeStrategys.get(optional.get());
    }

    private boolean dropPrimaryKey(AlterTableDropColumnEvent event) {
        CatalogTable modifyTable =
                ((List<CatalogTable>) schema.getProducedType())
                        .stream()
                                .filter(t -> t.getTablePath().equals(event.getTablePath()))
                                .findFirst()
                                .orElse(null);
        if (modifyTable != null && modifyTable.getTableSchema().getPrimaryKey() != null) {
            List<String> primaryKeys =
                    modifyTable.getTableSchema().getPrimaryKey().getColumnNames();
            if (primaryKeys.contains(event.getColumn())) {
                log.warn(
                        "The table {} cdc primary key column {} is dropped",
                        event.getTablePath(),
                        event.getColumn());
                return true;
            }
        }
        return false;
    }
}
