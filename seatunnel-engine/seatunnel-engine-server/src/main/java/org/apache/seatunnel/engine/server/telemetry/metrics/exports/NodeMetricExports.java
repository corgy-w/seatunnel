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

package org.apache.seatunnel.engine.server.telemetry.metrics.exports;

import org.apache.seatunnel.engine.server.service.slot.SlotMetricsSnapshot;
import org.apache.seatunnel.engine.server.service.slot.SlotService;
import org.apache.seatunnel.engine.server.telemetry.metrics.AbstractCollector;

import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.jmx.InstanceMBean;
import com.hazelcast.internal.jmx.ManagementService;
import com.hazelcast.internal.jmx.PartitionServiceMBean;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;

import java.util.ArrayList;
import java.util.List;

/**
 * Exports the node metrics of the SeaTunnel cluster.
 *
 * <p>These metrics are scoped to a single member and include Hazelcast executor/partition metrics
 * and worker slot/resource usage.
 */
public class NodeMetricExports extends AbstractCollector {

    public NodeMetricExports(Node node) {
        super(node);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        try {
            // instance state
            nodeState(mfs);

            String address = localAddress();
            try {
                slotAndResourceMetrics(mfs, address);
            } catch (Exception e) {
                getLogger(NodeMetricExports.class).warning("Failed to collect slot metrics", e);
            }

            ManagementService managementService = getManagementService();
            if (managementService == null) {
                getLogger(NodeMetricExports.class)
                        .warning(
                                "ManagementService is not available, skipping Hazelcast executor and partition metrics");
                return mfs;
            }
            if (managementService.getInstanceMBean() == null) {
                getLogger(NodeMetricExports.class)
                        .warning(
                                "ManagementService is not available, skipping Hazelcast executor and partition metrics");
                return mfs;
            }
            InstanceMBean instanceMBean = managementService.getInstanceMBean();

            // node hazelcast executor
            List<String> labelNames = clusterLabelNames(ADDRESS, "type");
            GaugeMetricFamily isShutdownMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_isShutdown",
                            "Whether the Hazelcast executor is shutdown (1) or not (0) on local member",
                            labelNames);
            GaugeMetricFamily isTerminatedMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_isTerminated",
                            "Whether the Hazelcast executor is terminated (1) or not (0) on local member",
                            labelNames);

            GaugeMetricFamily maxPoolSizeMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_maxPoolSize",
                            "Configured maximum pool size of Hazelcast executor on local member (threads)",
                            labelNames);

            GaugeMetricFamily poolSizeMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_poolSize",
                            "Current pool size of Hazelcast executor on local member (threads)",
                            labelNames);

            GaugeMetricFamily queueRemainingCapacityMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_queueRemainingCapacity",
                            "Remaining queue capacity of Hazelcast executor on local member",
                            labelNames);

            GaugeMetricFamily queueSizeMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_queueSize",
                            "Current queue size of Hazelcast executor on local member",
                            labelNames);

            GaugeMetricFamily executedCountMetricFamily =
                    new GaugeMetricFamily(
                            "hazelcast_executor_executedCount",
                            "Total executed task count of Hazelcast executor on local member",
                            labelNames);

            List<String> asyncValues = labelValues(address, "async");
            List<String> clientBlockingValues = labelValues(address, "clientBlocking");
            List<String> clientExecutorValues = labelValues(address, "client");
            List<String> clientQueryValues = labelValues(address, "clientQuery");
            List<String> ioValues = labelValues(address, "io");
            List<String> offloadableValues = labelValues(address, "offloadable");
            List<String> scheduledValues = labelValues(address, "scheduled");
            List<String> systemValues = labelValues(address, "system");

            // Executor executedCount
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().getExecutedCount(),
                    asyncValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getClientExecutorMBean().getExecutedCount(),
                    clientExecutorValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().getExecutedCount(),
                    clientBlockingValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().getExecutedCount(),
                    clientQueryValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getIoExecutorMBean().getExecutedCount(),
                    ioValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().getExecutedCount(),
                    offloadableValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().getExecutedCount(),
                    scheduledValues);
            longMetric(
                    executedCountMetricFamily,
                    instanceMBean.getSystemExecutorMBean().getExecutedCount(),
                    systemValues);
            mfs.add(executedCountMetricFamily);

            // Executor isShutdown
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().isShutdown() ? 1 : 0,
                    asyncValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getClientExecutorMBean().isShutdown() ? 1 : 0,
                    clientExecutorValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().isShutdown() ? 1 : 0,
                    clientBlockingValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().isShutdown() ? 1 : 0,
                    clientQueryValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getIoExecutorMBean().isShutdown() ? 1 : 0,
                    ioValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().isShutdown() ? 1 : 0,
                    offloadableValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().isShutdown() ? 1 : 0,
                    scheduledValues);
            intMetric(
                    isShutdownMetricFamily,
                    instanceMBean.getSystemExecutorMBean().isShutdown() ? 1 : 0,
                    systemValues);
            mfs.add(isShutdownMetricFamily);

            // Executor isTerminated
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().isTerminated() ? 1 : 0,
                    asyncValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getClientExecutorMBean().isTerminated() ? 1 : 0,
                    clientExecutorValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().isTerminated() ? 1 : 0,
                    clientBlockingValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().isTerminated() ? 1 : 0,
                    clientQueryValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getIoExecutorMBean().isTerminated() ? 1 : 0,
                    ioValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().isTerminated() ? 1 : 0,
                    offloadableValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().isTerminated() ? 1 : 0,
                    scheduledValues);
            intMetric(
                    isTerminatedMetricFamily,
                    instanceMBean.getSystemExecutorMBean().isTerminated() ? 1 : 0,
                    systemValues);
            mfs.add(isTerminatedMetricFamily);

            // Executor maxPoolSize
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().maxPoolSize(),
                    asyncValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getClientExecutorMBean().maxPoolSize(),
                    clientExecutorValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().maxPoolSize(),
                    clientBlockingValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().maxPoolSize(),
                    clientQueryValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getIoExecutorMBean().maxPoolSize(),
                    ioValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().maxPoolSize(),
                    offloadableValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().maxPoolSize(),
                    scheduledValues);
            intMetric(
                    maxPoolSizeMetricFamily,
                    instanceMBean.getSystemExecutorMBean().maxPoolSize(),
                    systemValues);
            mfs.add(maxPoolSizeMetricFamily);

            // Executor poolSize
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().poolSize(),
                    asyncValues);
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getClientExecutorMBean().poolSize(),
                    clientExecutorValues);
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().poolSize(),
                    clientBlockingValues);
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().poolSize(),
                    clientQueryValues);
            intMetric(
                    poolSizeMetricFamily, instanceMBean.getIoExecutorMBean().poolSize(), ioValues);
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().poolSize(),
                    offloadableValues);
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().poolSize(),
                    scheduledValues);
            intMetric(
                    poolSizeMetricFamily,
                    instanceMBean.getSystemExecutorMBean().poolSize(),
                    systemValues);
            mfs.add(poolSizeMetricFamily);

            // Executor queueRemainingCapacity
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().queueRemainingCapacity(),
                    asyncValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getClientExecutorMBean().queueRemainingCapacity(),
                    clientExecutorValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().queueRemainingCapacity(),
                    clientBlockingValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().queueRemainingCapacity(),
                    clientQueryValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getIoExecutorMBean().queueRemainingCapacity(),
                    ioValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().queueRemainingCapacity(),
                    offloadableValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().queueRemainingCapacity(),
                    scheduledValues);
            intMetric(
                    queueRemainingCapacityMetricFamily,
                    instanceMBean.getSystemExecutorMBean().queueRemainingCapacity(),
                    systemValues);
            mfs.add(queueRemainingCapacityMetricFamily);

            // Executor queueSize
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getAsyncExecutorMBean().queueSize(),
                    asyncValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getClientExecutorMBean().queueSize(),
                    clientExecutorValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getClientBlockingExecutorMBean().queueSize(),
                    clientBlockingValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getClientQueryExecutorMBean().queueSize(),
                    clientQueryValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getIoExecutorMBean().queueSize(),
                    ioValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getOffloadableExecutorMBean().queueSize(),
                    offloadableValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getScheduledExecutorMBean().queueSize(),
                    scheduledValues);
            intMetric(
                    queueSizeMetricFamily,
                    instanceMBean.getSystemExecutorMBean().queueSize(),
                    systemValues);
            mfs.add(queueSizeMetricFamily);

            // partition metric
            partitionMetric(instanceMBean.getPartitionServiceMBean(), mfs, address);
        } catch (Exception e) {
            getLogger(NodeMetricExports.class).warning("Failed to collect node metrics", e);
        }
        return mfs;
    }

    private void slotAndResourceMetrics(List<MetricFamilySamples> mfs, String address) {
        if (getServer() == null || getServer().getSlotService() == null) {
            return;
        }
        SlotService slotService = getServer().getSlotService();
        SlotMetricsSnapshot slotMetricsSnapshot = slotService.getSlotMetrics();

        // In dynamic-slot mode, DefaultSlotService may keep no pre-created slots until jobs
        // request them. For observability, use the configured slotNum as the worker slot capacity
        // so dashboards can show meaningful totals even when the cluster is idle.
        int configuredTotalSlots =
                getServer()
                        .getSeaTunnelConfig()
                        .getEngineConfig()
                        .getSlotServiceConfig()
                        .getSlotNum();

        int assignedSlots = slotMetricsSnapshot.getAssignedSlotCount();
        int totalSlots = configuredTotalSlots;
        int unassignedSlots = Math.max(0, totalSlots - assignedSlots);
        double slotUtilizationRatio = totalSlots <= 0 ? 0D : (double) assignedSlots / totalSlots;

        List<String> labelNames = clusterLabelNames(ADDRESS);
        List<String> labelValues = labelValues(address);

        GaugeMetricFamily slotTotal =
                new GaugeMetricFamily(
                        "slot_total", "Configured slot capacity of current worker", labelNames);
        intMetric(slotTotal, totalSlots, labelValues);
        mfs.add(slotTotal);

        GaugeMetricFamily slotAssigned =
                new GaugeMetricFamily(
                        "slot_assigned", "Assigned slots of current worker", labelNames);
        intMetric(slotAssigned, assignedSlots, labelValues);
        mfs.add(slotAssigned);

        GaugeMetricFamily slotUnassigned =
                new GaugeMetricFamily(
                        "slot_unassigned", "Free slot capacity of current worker", labelNames);
        intMetric(slotUnassigned, unassignedSlots, labelValues);
        mfs.add(slotUnassigned);

        GaugeMetricFamily slotUtilization =
                new GaugeMetricFamily(
                        "slot_utilization_ratio",
                        "Slot utilization ratio of current worker (assigned/configured_total)",
                        labelNames);
        slotUtilization.addMetric(labelValues, slotUtilizationRatio);
        mfs.add(slotUtilization);

        CounterMetricFamily slotRequestTotal =
                new CounterMetricFamily(
                        "slot_request",
                        "Slot request total of current worker",
                        clusterLabelNames(ADDRESS, "result"));
        slotRequestTotal.addMetric(
                labelValues(address, "success"), slotMetricsSnapshot.getSlotRequestSuccessTotal());
        slotRequestTotal.addMetric(
                labelValues(address, "failure"), slotMetricsSnapshot.getSlotRequestFailureTotal());
        mfs.add(slotRequestTotal);

        CounterMetricFamily slotReleaseTotal =
                new CounterMetricFamily(
                        "slot_release",
                        "Slot release total of current worker",
                        clusterLabelNames(ADDRESS));
        slotReleaseTotal.addMetric(labelValues, slotMetricsSnapshot.getSlotReleaseTotal());
        mfs.add(slotReleaseTotal);
    }

    private void partitionMetric(
            PartitionServiceMBean partitionServiceMBean,
            List<MetricFamilySamples> mfs,
            String address) {
        List<String> labelNames = clusterLabelNames(ADDRESS);

        GaugeMetricFamily partitionPartitionCount =
                new GaugeMetricFamily(
                        "hazelcast_partition_partitionCount",
                        "Total partition count of SeaTunnel cluster",
                        labelNames);
        intMetric(
                partitionPartitionCount,
                partitionServiceMBean.getPartitionCount(),
                labelValues(address));
        mfs.add(partitionPartitionCount);

        GaugeMetricFamily partitionActivePartition =
                new GaugeMetricFamily(
                        "hazelcast_partition_activePartition",
                        "Active partition count of local member",
                        labelNames);
        intMetric(
                partitionActivePartition,
                partitionServiceMBean.getActivePartitionCount(),
                labelValues(address));
        mfs.add(partitionActivePartition);

        GaugeMetricFamily partitionIsClusterSafe =
                new GaugeMetricFamily(
                        "hazelcast_partition_isClusterSafe",
                        "Whether the cluster is in a safe state (1) or not (0)",
                        labelNames);
        intMetric(
                partitionIsClusterSafe,
                partitionServiceMBean.isClusterSafe() ? 1 : 0,
                labelValues(address));
        mfs.add(partitionIsClusterSafe);

        GaugeMetricFamily partitionIsLocalMemberSafe =
                new GaugeMetricFamily(
                        "hazelcast_partition_isLocalMemberSafe",
                        "Whether the local member is safe to shutdown (1) or not (0)",
                        labelNames);
        intMetric(
                partitionIsLocalMemberSafe,
                partitionServiceMBean.isLocalMemberSafe() ? 1 : 0,
                labelValues(address));
        mfs.add(partitionIsLocalMemberSafe);
    }

    private void nodeState(List<MetricFamilySamples> mfs) {
        GaugeMetricFamily metricFamily =
                new GaugeMetricFamily(
                        "node_state",
                        "Node up probe (1 = up, 0 = down)",
                        clusterLabelNames(ADDRESS));
        String address = localAddress();
        List<String> labelValues = labelValues(address);
        metricFamily.addMetric(labelValues, 1);
        mfs.add(metricFamily);
    }
}
