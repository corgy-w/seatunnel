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

package org.apache.seatunnel.api.sink.event.savemode;

import org.apache.seatunnel.api.event.EventType;
import org.apache.seatunnel.api.table.catalog.TablePath;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SaveModeFinishedEvent implements SaveModeEvent {
    private final String databaseName;
    private final String schemaName;
    private final String tableName;
    private int indexOfTable;
    private long startTime;
    private long finishedTime;

    private String jobId;
    private final long createdTime;
    private EventType eventType = EventType.SAVEMODE_FINISHED;

    public SaveModeFinishedEvent(
            long jobId, TablePath tablePath, int indexOfTable, long startTime, long finishedTime) {
        this.databaseName = tablePath.getDatabaseName();
        this.schemaName = tablePath.getSchemaName();
        this.tableName = tablePath.getTableName();
        this.jobId = String.valueOf(jobId);
        this.indexOfTable = indexOfTable;
        this.startTime = startTime;
        this.finishedTime = finishedTime;
        this.createdTime = System.currentTimeMillis();
    }
}
