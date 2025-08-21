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

import org.apache.seatunnel.api.source.SourceSplit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * PI datasplit For allocating WebIDs to different processing tasks，Supports large-scale PI data
 * source parallel processing
 */
public class PISplit implements SourceSplit {
    private static final long serialVersionUID = 1L;

    private final String splitId;
    private final List<String> webIds;

    private final long lastCheckpointTime;

    // batch mode specific fields
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private boolean completed;
    private int retryCount;

    public PISplit(String splitId, List<String> webIds) {
        this(splitId, webIds, 0L);
    }

    public PISplit(String splitId, List<String> webIds, long lastCheckpointTime) {
        this.splitId = splitId;
        this.webIds = webIds;
        this.lastCheckpointTime = lastCheckpointTime;
        this.startTime = null;
        this.endTime = null;
        this.completed = false;
        this.retryCount = 0;
    }

    /** Batch mode constructor with time range */
    public PISplit(
            String splitId, List<String> webIds, LocalDateTime startTime, LocalDateTime endTime) {
        this.splitId = splitId;
        this.webIds = webIds;
        this.lastCheckpointTime = 0L;
        this.startTime = startTime;
        this.endTime = endTime;
        this.completed = false;
        this.retryCount = 0;
    }

    @Override
    public String splitId() {
        return splitId;
    }

    // add compatible methods
    public String getSplitId() {
        return splitId;
    }

    public List<String> getWebIds() {
        return webIds;
    }

    public long getLastCheckpointTime() {
        return lastCheckpointTime;
    }

    // ================= batch mode specific methods =================

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    /** Get split size (number of contained WebIDs) */
    public int getSize() {
        return webIds != null ? webIds.size() : 0;
    }

    /** Check if split is empty */
    public boolean isEmpty() {
        return webIds == null || webIds.isEmpty();
    }

    /** Create split copy with new checkpoint time */
    public PISplit withCheckpointTime(long checkpointTime) {
        return new PISplit(splitId, webIds, checkpointTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PISplit piSplit = (PISplit) o;
        return lastCheckpointTime == piSplit.lastCheckpointTime
                && Objects.equals(splitId, piSplit.splitId)
                && Objects.equals(webIds, piSplit.webIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitId, webIds, lastCheckpointTime);
    }

    @Override
    public String toString() {
        return "PISplit{"
                + "splitId='"
                + splitId
                + '\''
                + ", webIdCount="
                + getSize()
                + ", lastCheckpointTime="
                + lastCheckpointTime
                + '}';
    }
}
