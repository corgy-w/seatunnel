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

package org.apache.seatunnel.api.source.event;

import org.apache.seatunnel.api.event.Event;
import org.apache.seatunnel.api.source.SourceEvent;
import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.api.table.catalog.TablePath;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class EnumeratorEventRecorder {
    private final SourceSplitEnumerator.Context<?> context;
    private final Map<TablePath, Integer> splitRemainCount;

    public EnumeratorEventRecorder(SourceSplitEnumerator.Context<?> context) {
        this.context = context;
        this.splitRemainCount = new ConcurrentHashMap<>();
    }

    public void addTableSplit(TablePath tablePath, int splitCount) {
        if (tablePath == null) {
            tablePath = TablePath.DEFAULT;
        }
        if (!splitRemainCount.containsKey(tablePath)) {
            context.getEventListener().onEvent(new TableReadStartedEvent(tablePath));
            splitRemainCount.put(tablePath, splitCount);
        } else {
            log.warn("Table {} already has splits, ignore the new splits", tablePath);
        }
    }

    public void recordEvent(SourceEvent event) {
        if (event instanceof ReaderSplitFinishedEvent) {
            ReaderSplitFinishedEvent e = (ReaderSplitFinishedEvent) event;
            context.getEventListener().onEvent(e);
            TablePath path;
            if (e.getTableName() == null) {
                path = TablePath.DEFAULT;
            } else {
                path = TablePath.of(e.getDatabaseName(), e.getSchemaName(), e.getTableName());
            }
            if (splitRemainCount.containsKey(path)) {
                splitRemainCount.computeIfPresent(path, (k, v) -> v - 1);
                if (splitRemainCount.get(path) == 0) {
                    splitRemainCount.remove(path);
                    context.getEventListener()
                            .onEvent(
                                    new TableReadFinishedEvent(
                                            TablePath.of(
                                                    e.getDatabaseName(),
                                                    e.getSchemaName(),
                                                    StringUtils.isNotEmpty(e.getTableName())
                                                            ? e.getTableName()
                                                            : TablePath.DEFAULT.getTableName())));
                }
            }
        } else if (event instanceof Event) {
            context.getEventListener().onEvent((Event) event);
        } else {
            log.warn("Unsupported event: {}", event);
        }
    }
}
