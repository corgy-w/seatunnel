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

package org.apache.seatunnel.engine.server.task.event;

import org.apache.seatunnel.api.event.EventListener;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.event.SchemaChangeEvent;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.engine.server.event.DataReadStatisticsEvent;
import org.apache.seatunnel.engine.server.event.DataWriteStatisticsEvent;
import org.apache.seatunnel.engine.server.execution.TaskLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DataStatisticsRecorder {

    private final Map<String, Long> read;
    private final Map<String, Long> insert;
    private final Map<String, Long> delete;
    private final Map<String, Long> update;
    private final Map<String, Long> ddl;
    private final Set<String> tables;
    private final EventListener eventListener;
    private final TaskLocation taskLocation;
    private final PluginType pluginType;
    private static final long TIME_INTERVAL = 5000L;
    private long lastTimeSent;

    public DataStatisticsRecorder(
            EventListener eventListener, TaskLocation taskLocation, PluginType pluginType) {
        this.read = new HashMap<>();
        this.insert = new HashMap<>();
        this.delete = new HashMap<>();
        this.update = new HashMap<>();
        this.ddl = new HashMap<>();
        this.tables = new HashSet<>();
        this.taskLocation = taskLocation;
        this.eventListener = eventListener;
        this.pluginType = pluginType;
        this.lastTimeSent = System.currentTimeMillis();
    }

    public void updateStatistics(SeaTunnelRow row) {
        String tablePath = row.getTableId();
        tables.add(tablePath);
        switch (row.getRowKind()) {
            case INSERT:
                insert.put(tablePath, insert.getOrDefault(tablePath, 0L) + 1);
                break;
            case READ:
                read.put(tablePath, read.getOrDefault(tablePath, 0L) + 1);
                break;
            case DELETE:
                delete.put(tablePath, delete.getOrDefault(tablePath, 0L) + 1);
                break;
            case UPDATE_AFTER:
                update.put(tablePath, update.getOrDefault(tablePath, 0L) + 1);
                break;
            default:
                break;
        }
        if (lastTimeSent + TIME_INTERVAL < System.currentTimeMillis()) {
            timeToSendStatistics();
        }
    }

    public void updateStatistics(SchemaChangeEvent event) {
        String tablePath = event.tablePath().toString();
        tables.add(tablePath);
        ddl.put(tablePath, ddl.getOrDefault(tablePath, 0L) + 1);
        if (lastTimeSent + TIME_INTERVAL < System.currentTimeMillis()) {
            timeToSendStatistics();
        }
    }

    public synchronized void timeToSendStatistics() {
        for (String table : tables) {
            long r = read.getOrDefault(table, 0L);
            long i = insert.getOrDefault(table, 0L);
            long d = delete.getOrDefault(table, 0L);
            long u = update.getOrDefault(table, 0L);
            long dd = ddl.getOrDefault(table, 0L);
            read.remove(table);
            insert.remove(table);
            delete.remove(table);
            update.remove(table);
            ddl.remove(table);
            if (r + i + d + u + dd > 0) {
                if (pluginType.equals(PluginType.SOURCE)) {
                    eventListener.onEvent(
                            new DataReadStatisticsEvent(
                                    table != null ? TablePath.of(table) : null,
                                    r,
                                    i,
                                    d,
                                    u,
                                    dd,
                                    taskLocation.getTaskID()));
                } else {
                    eventListener.onEvent(
                            new DataWriteStatisticsEvent(
                                    table != null ? TablePath.of(table) : null,
                                    r,
                                    i,
                                    d,
                                    u,
                                    dd,
                                    taskLocation.getTaskID()));
                }
            }
        }
        lastTimeSent = System.currentTimeMillis();
    }
}
