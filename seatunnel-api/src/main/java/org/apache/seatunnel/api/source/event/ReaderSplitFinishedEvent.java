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
import org.apache.seatunnel.api.event.EventType;
import org.apache.seatunnel.api.source.SourceEvent;
import org.apache.seatunnel.api.source.SourceSplit;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReaderSplitFinishedEvent implements Event, SourceEvent {
    private String databaseName;
    private String schemaName;
    private String tableName;
    private int splitIndex;
    private int splitCount;
    private long createdTime;
    private String jobId;
    private EventType eventType = EventType.READER_SPLIT_READ_FINISHED;

    public ReaderSplitFinishedEvent(SourceSplit split) {
        if (split.getTablePath() != null) {
            this.databaseName = split.getTablePath().getDatabaseName();
            this.schemaName = split.getTablePath().getSchemaName();
            this.tableName = split.getTablePath().getTableName();
        }
        this.splitIndex = split.getIndex();
        this.splitCount = split.getSplitCount();
        this.createdTime = System.currentTimeMillis();
    }
}
