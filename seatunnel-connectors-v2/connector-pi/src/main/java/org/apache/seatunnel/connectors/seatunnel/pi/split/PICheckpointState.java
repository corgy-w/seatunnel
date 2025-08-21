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

package org.apache.seatunnel.connectors.seatunnel.pi.split;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PI checkpoint state for tracking the latest timestamp of each WebID and disconnection recovery
 * state
 */
public class PICheckpointState implements Serializable {
    private static final long serialVersionUID = 1L;

    // Checkpoint ID
    private long checkpointId;

    // Latest timestamp for each WebID
    private final Map<String, LocalDateTime> webIdTimestamps = new HashMap<>();

    // Disconnection recovery state
    private LocalDateTime disconnectStartTime;
    private LocalDateTime lastMessageTime;

    public PICheckpointState() {
        this.lastMessageTime = LocalDateTime.now();
    }

    /** Update WebID timestamp */
    public void updateState(String webId, LocalDateTime timestamp) {
        if (webId != null && timestamp != null) {
            webIdTimestamps.put(webId, timestamp);
            lastMessageTime = LocalDateTime.now();
        }
    }

    /** Get the latest timestamp for WebID */
    public LocalDateTime getTimestamp(String webId) {
        return webIdTimestamps.getOrDefault(webId, null);
    }

    /** Get the earliest recovery time for disconnection data compensation */
    public LocalDateTime getEarliestRecoveryTime() {
        if (disconnectStartTime != null) {
            return disconnectStartTime;
        }

        // If there's no explicit disconnection time, use the time of the last message
        if (lastMessageTime != null) {
            return lastMessageTime;
        }

        // Default to current time minus 5 minutes
        return LocalDateTime.now().minusMinutes(5);
    }

    /** Set disconnection start time */
    public void setDisconnectStartTime(LocalDateTime disconnectStartTime) {
        this.disconnectStartTime = disconnectStartTime;
    }

    /** Get disconnection start time */
    public LocalDateTime getDisconnectStartTime() {
        return disconnectStartTime;
    }

    /** Get last message time */
    public LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    /** Set last message time */
    public void setLastMessageTime(LocalDateTime lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    /** Get checkpoint ID */
    public long getCheckpointId() {
        return checkpointId;
    }

    /** Set checkpoint ID */
    public void setCheckpointId(long checkpointId) {
        this.checkpointId = checkpointId;
    }

    /** Get timestamps for all WebIDs */
    public Map<String, LocalDateTime> getWebIdTimestamps() {
        return new HashMap<>(webIdTimestamps);
    }

    /** Clear state */
    public void clear() {
        webIdTimestamps.clear();
        disconnectStartTime = null;
    }
}
