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

package org.apache.seatunnel.engine.server.event;

import org.apache.seatunnel.api.event.Event;
import org.apache.seatunnel.api.event.EventType;
import org.apache.seatunnel.api.table.catalog.TablePath;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DataWriteStatisticsEvent implements Event {

    private long read;
    private long insert;
    private long delete;
    private long update;
    private long ddl;
    private String databaseName;
    private String schemaName;
    private String tableName;
    private long taskId;

    public DataWriteStatisticsEvent(
            TablePath tablePath,
            long read,
            long insert,
            long delete,
            long update,
            long ddl,
            long taskId) {
        if (tablePath != null) {
            this.databaseName = tablePath.getDatabaseName();
            this.schemaName = tablePath.getSchemaName();
            this.tableName = tablePath.getTableName();
        }
        this.read = read;
        this.insert = insert;
        this.delete = delete;
        this.update = update;
        this.ddl = ddl;
        this.taskId = taskId;
        this.createdTime = System.currentTimeMillis();
    }

    private long createdTime;
    private String jobId;
    private EventType eventType = EventType.DATA_WRITE_STATISTICS;
}
