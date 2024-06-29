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

import org.apache.seatunnel.shade.com.google.common.collect.ImmutableMap;

import org.apache.seatunnel.api.event.EventType;
import org.apache.seatunnel.api.table.schema.event.AlterTableColumnsEvent;
import org.apache.seatunnel.api.table.schema.event.SchemaChangeEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SchemaChangeEventRecognizer {
    private static final Map<EventType, Function<SchemaChangeEvent, SchemaChangeType>> REGISTRIES =
            ImmutableMap.of(
                    EventType.SCHEMA_CHANGE_ADD_COLUMN,
                    event -> SchemaChangeType.ADD_COLUMN,
                    EventType.SCHEMA_CHANGE_DROP_COLUMN,
                    event -> SchemaChangeType.DROP_COLUMN,
                    EventType.SCHEMA_CHANGE_MODIFY_COLUMN,
                    event -> SchemaChangeType.UPDATE_COLUMN,
                    EventType.SCHEMA_CHANGE_CHANGE_COLUMN,
                    event -> SchemaChangeType.RENAME_COLUMN,
                    EventType.SCHEMA_CHANGE_UPDATE_COLUMNS,
                    event -> {
                        AlterTableColumnsEvent columnsEvent = (AlterTableColumnsEvent) event;
                        Set<EventType> eventTyeps =
                                columnsEvent.getEvents().stream()
                                        .map(e -> e.getEventType())
                                        .collect(Collectors.toSet());
                        if (eventTyeps.contains(EventType.SCHEMA_CHANGE_CHANGE_COLUMN)) {
                            return SchemaChangeType.RENAME_COLUMN;
                        }
                        if (eventTyeps.contains(EventType.SCHEMA_CHANGE_MODIFY_COLUMN)) {
                            return SchemaChangeType.UPDATE_COLUMN;
                        }
                        if (eventTyeps.contains(EventType.SCHEMA_CHANGE_DROP_COLUMN)) {
                            return SchemaChangeType.DROP_COLUMN;
                        }
                        return SchemaChangeType.ADD_COLUMN;
                    });

    public static Optional<SchemaChangeType> recognize(SchemaChangeEvent event) {
        if (REGISTRIES.containsKey(event.getEventType())) {
            return Optional.of(REGISTRIES.get(event.getEventType()).apply(event));
        }
        return Optional.empty();
    }
}
