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

package org.apache.seatunnel.engine.server.operation;

import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.engine.core.metrics.ClusterNodeMetrics;
import org.apache.seatunnel.engine.core.metrics.HealthMetrics;
import org.apache.seatunnel.engine.core.metrics.SlotMetrics;
import org.apache.seatunnel.engine.server.SeaTunnelHealthMonitor;
import org.apache.seatunnel.engine.server.SeaTunnelServer;
import org.apache.seatunnel.engine.server.serializable.ClientToServerOperationDataSerializerHook;
import org.apache.seatunnel.engine.server.service.slot.SlotMetricsSnapshot;
import org.apache.seatunnel.engine.server.service.slot.SlotService;

import com.hazelcast.cluster.Address;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.impl.AllowedDuringPassiveState;
import com.hazelcast.spi.impl.operationservice.Operation;

import java.util.LinkedHashMap;
import java.util.Map;

public class GetClusterNodeMetricsOperation extends Operation
        implements IdentifiedDataSerializable, AllowedDuringPassiveState {

    private String response;

    public GetClusterNodeMetricsOperation() {}

    @Override
    public int getFactoryId() {
        return ClientToServerOperationDataSerializerHook.FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return ClientToServerOperationDataSerializerHook.GET_CLUSTER_NODE_METRICS;
    }

    @Override
    public void run() {
        SeaTunnelServer service = getService();
        Address thisAddress = getNodeEngine().getThisAddress();
        Address masterAddress = getNodeEngine().getMasterAddress();

        ClusterNodeMetrics clusterNodeMetrics = new ClusterNodeMetrics();
        clusterNodeMetrics.setHost(thisAddress.getHost());
        clusterNodeMetrics.setPort(thisAddress.getPort());
        clusterNodeMetrics.setIsMaster(masterAddress != null && masterAddress.equals(thisAddress));
        clusterNodeMetrics.setSlot(buildSlotMetrics(service));
        clusterNodeMetrics.setHealth(buildHealthMetrics(service));
        clusterNodeMetrics.setExtensions(new LinkedHashMap<>());

        response = JsonUtils.toJsonString(clusterNodeMetrics);
    }

    private SlotMetrics buildSlotMetrics(SeaTunnelServer service) {
        SlotService slotService = service.getSlotService();
        if (slotService == null) {
            return null;
        }

        boolean dynamicSlot =
                service.getSeaTunnelConfig()
                        .getEngineConfig()
                        .getSlotServiceConfig()
                        .isDynamicSlot();
        SlotMetricsSnapshot slotMetricsSnapshot = slotService.getSlotMetrics();
        int assignedSlots = slotMetricsSnapshot.getAssignedSlotCount();
        int unassignedSlots = slotMetricsSnapshot.getUnassignedSlotCount();
        int totalSlots = assignedSlots + unassignedSlots;
        if (totalSlots <= 0) {
            totalSlots =
                    service.getSeaTunnelConfig()
                            .getEngineConfig()
                            .getSlotServiceConfig()
                            .getSlotNum();
        }
        double slotUtilizationRatio = totalSlots <= 0 ? 0D : (double) assignedSlots / totalSlots;

        return new SlotMetrics(
                dynamicSlot,
                totalSlots,
                assignedSlots,
                unassignedSlots,
                slotUtilizationRatio,
                slotMetricsSnapshot.getSlotRequestSuccessTotal(),
                slotMetricsSnapshot.getSlotRequestFailureTotal(),
                slotMetricsSnapshot.getSlotReleaseTotal());
    }

    private HealthMetrics buildHealthMetrics(SeaTunnelServer service) {
        SeaTunnelHealthMonitor healthMonitor = service.getSeaTunnelHealthMonitor();
        if (healthMonitor == null || healthMonitor.getHealthMetrics() == null) {
            return null;
        }
        return new HealthMetrics(parseHealthMetrics(healthMonitor.getHealthMetrics().render()));
    }

    private Map<String, String> parseHealthMetrics(String renderedMetrics) {
        Map<String, String> metrics = new LinkedHashMap<>();
        if (renderedMetrics == null || renderedMetrics.isEmpty()) {
            return metrics;
        }
        String[] parts = renderedMetrics.split(", ");
        for (String part : parts) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            metrics.put(keyValue[0].trim(), keyValue[1].trim());
        }
        return metrics;
    }

    @Override
    public Object getResponse() {
        return response;
    }
}
