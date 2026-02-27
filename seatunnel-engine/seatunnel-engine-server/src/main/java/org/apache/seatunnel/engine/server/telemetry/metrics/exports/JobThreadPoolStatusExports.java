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

import org.apache.seatunnel.engine.server.telemetry.metrics.AbstractCollector;
import org.apache.seatunnel.engine.server.telemetry.metrics.entity.ThreadPoolStatus;

import com.hazelcast.instance.impl.Node;
import io.prometheus.client.CounterMetricFamily;
import io.prometheus.client.GaugeMetricFamily;

import java.util.ArrayList;
import java.util.List;

/**
 * Exports the job thread pool status metrics of the SeaTunnel coordinator.
 *
 * <p>These metrics describe the coordinator executor thread pool utilization and workload.
 */
public class JobThreadPoolStatusExports extends AbstractCollector {

    public JobThreadPoolStatusExports(Node node) {
        super(node);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        try {
            ThreadPoolStatus threadPoolStatusMetrics =
                    getServer() == null ? null : getServer().getThreadPoolStatusMetrics();
            if (threadPoolStatusMetrics == null) {
                getLogger(JobThreadPoolStatusExports.class)
                        .warning("ThreadPoolStatus is not available, skipping thread pool metrics");
                return mfs;
            }

            List<String> labelValues = labelValues(localAddress());
            List<String> labelNames = clusterLabelNames(ADDRESS);

            GaugeMetricFamily activeCount =
                    new GaugeMetricFamily(
                            "job_thread_pool_activeCount",
                            "Active thread count of coordinator job executor thread pool",
                            labelNames);
            activeCount.addMetric(labelValues, threadPoolStatusMetrics.getActiveCount());
            mfs.add(activeCount);

            CounterMetricFamily completedTask =
                    new CounterMetricFamily(
                            "job_thread_pool_completedTask",
                            "Total completed task count of coordinator job executor thread pool",
                            labelNames);
            completedTask.addMetric(labelValues, threadPoolStatusMetrics.getCompletedTaskCount());
            mfs.add(completedTask);

            GaugeMetricFamily corePoolSize =
                    new GaugeMetricFamily(
                            "job_thread_pool_corePoolSize",
                            "Configured core pool size of coordinator job executor thread pool (threads)",
                            labelNames);
            corePoolSize.addMetric(labelValues, threadPoolStatusMetrics.getCorePoolSize());
            mfs.add(corePoolSize);

            GaugeMetricFamily maximumPoolSize =
                    new GaugeMetricFamily(
                            "job_thread_pool_maximumPoolSize",
                            "Configured maximum pool size of coordinator job executor thread pool (threads)",
                            labelNames);
            maximumPoolSize.addMetric(labelValues, threadPoolStatusMetrics.getMaximumPoolSize());
            mfs.add(maximumPoolSize);

            GaugeMetricFamily poolSize =
                    new GaugeMetricFamily(
                            "job_thread_pool_poolSize",
                            "Current pool size of coordinator job executor thread pool (threads)",
                            labelNames);
            poolSize.addMetric(labelValues, threadPoolStatusMetrics.getPoolSize());
            mfs.add(poolSize);

            CounterMetricFamily taskCount =
                    new CounterMetricFamily(
                            "job_thread_pool_task",
                            "Total task count of coordinator job executor thread pool",
                            labelNames);
            taskCount.addMetric(labelValues, threadPoolStatusMetrics.getTaskCount());
            mfs.add(taskCount);

            GaugeMetricFamily queueTaskCount =
                    new GaugeMetricFamily(
                            "job_thread_pool_queueTaskCount",
                            "Current queued task count of coordinator job executor thread pool",
                            labelNames);
            queueTaskCount.addMetric(labelValues, threadPoolStatusMetrics.getQueueTaskCount());
            mfs.add(queueTaskCount);

            CounterMetricFamily rejectionCount =
                    new CounterMetricFamily(
                            "job_thread_pool_rejection",
                            "Total rejected task count of coordinator job executor thread pool",
                            labelNames);
            rejectionCount.addMetric(labelValues, threadPoolStatusMetrics.getRejectionCount());
            mfs.add(rejectionCount);
        } catch (Exception e) {
            getLogger(JobThreadPoolStatusExports.class)
                    .warning("Failed to collect thread pool metrics", e);
        }

        return mfs;
    }
}
