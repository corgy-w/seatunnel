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

package org.apache.seatunnel.engine.server.service.slot;

import java.io.Serializable;

/**
 * SlotMetricsSnapshot is a point-in-time view of slot related metrics.
 *
 * <p>This snapshot is intended for telemetry and diagnostics. It avoids multiple method calls when
 * collecting several related metrics from {@link SlotService}.
 */
public class SlotMetricsSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int assignedSlotCount;
    private final int unassignedSlotCount;
    private final long slotRequestSuccessTotal;
    private final long slotRequestFailureTotal;
    private final long slotReleaseTotal;

    public SlotMetricsSnapshot(
            int assignedSlotCount,
            int unassignedSlotCount,
            long slotRequestSuccessTotal,
            long slotRequestFailureTotal,
            long slotReleaseTotal) {
        this.assignedSlotCount = assignedSlotCount;
        this.unassignedSlotCount = unassignedSlotCount;
        this.slotRequestSuccessTotal = slotRequestSuccessTotal;
        this.slotRequestFailureTotal = slotRequestFailureTotal;
        this.slotReleaseTotal = slotReleaseTotal;
    }

    public int getAssignedSlotCount() {
        return assignedSlotCount;
    }

    public int getUnassignedSlotCount() {
        return unassignedSlotCount;
    }

    public long getSlotRequestSuccessTotal() {
        return slotRequestSuccessTotal;
    }

    public long getSlotRequestFailureTotal() {
        return slotRequestFailureTotal;
    }

    public long getSlotReleaseTotal() {
        return slotReleaseTotal;
    }
}
