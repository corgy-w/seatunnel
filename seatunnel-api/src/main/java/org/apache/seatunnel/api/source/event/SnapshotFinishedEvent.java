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

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class SnapshotFinishedEvent implements Event, SourceEvent {
    private long createdTime;
    private String jobId;
    private EventType eventType = EventType.SNAPSHOT_FINISHED;

    public SnapshotFinishedEvent() {
        this.createdTime = System.currentTimeMillis();
    }
}
