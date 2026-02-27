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

package org.apache.seatunnel.engine.server;

import org.apache.seatunnel.engine.common.config.ConfigProvider;
import org.apache.seatunnel.engine.common.config.SeaTunnelConfig;
import org.apache.seatunnel.engine.server.telemetry.metrics.ExportsInstanceInitializer;

import com.hazelcast.instance.impl.HazelcastInstanceFactory;
import com.hazelcast.instance.impl.HazelcastInstanceImpl;
import com.hazelcast.instance.impl.HazelcastInstanceProxy;
import com.hazelcast.instance.impl.Node;
import lombok.NonNull;

public class SeaTunnelServerStarter {

    public static void main(String[] args) {
        createHazelcastInstance();
    }

    public static HazelcastInstanceImpl createHazelcastInstance(String clusterName) {
        SeaTunnelConfig seaTunnelConfig = ConfigProvider.locateAndGetSeaTunnelConfig();
        seaTunnelConfig.getHazelcastConfig().setClusterName(clusterName);
        return createHazelcastInstance(seaTunnelConfig);
    }

    public static HazelcastInstanceImpl createHazelcastInstance(
            @NonNull SeaTunnelConfig seaTunnelConfig) {
        return createHazelcastInstance(seaTunnelConfig, null);
    }

    public static HazelcastInstanceImpl createHazelcastInstance(
            @NonNull SeaTunnelConfig seaTunnelConfig, String customInstanceName) {
        return initializeHazelcastInstance(seaTunnelConfig, customInstanceName);
    }

    private static HazelcastInstanceImpl initializeHazelcastInstance(
            @NonNull SeaTunnelConfig seaTunnelConfig, String customInstanceName) {
        boolean condition = checkTelemetryConfig(seaTunnelConfig);
        String instanceName =
                customInstanceName != null
                        ? customInstanceName
                        : HazelcastInstanceFactory.createInstanceName(
                                seaTunnelConfig.getHazelcastConfig());

        HazelcastInstanceImpl original =
                ((HazelcastInstanceProxy)
                                HazelcastInstanceFactory.newHazelcastInstance(
                                        seaTunnelConfig.getHazelcastConfig(),
                                        instanceName,
                                        new SeaTunnelNodeContext(seaTunnelConfig)))
                        .getOriginal();
        // init telemetry instance
        if (condition) {
            initTelemetryInstance(original.node);
        }
        return original;
    }

    public static HazelcastInstanceImpl createHazelcastInstance() {
        SeaTunnelConfig seaTunnelConfig = ConfigProvider.locateAndGetSeaTunnelConfig();
        return createHazelcastInstance(seaTunnelConfig);
    }

    /**
     * Initializes the telemetry metrics system by registering all collectors.
     *
     * <p>This method triggers the registration of custom metric collectors including:
     *
     * <ul>
     *   <li>ClusterMetricExports - cluster-level metrics (version, time, node count)
     *   <li>JobMetricExports - job statistics (created, running, finished, failed, cancelled)
     *   <li>JobThreadPoolStatusExports - thread pool monitoring metrics
     *   <li>NodeMetricExports - node-level metrics (executors, partitions)
     * </ul>
     *
     * <p>Additionally, JVM metrics are automatically registered via {@code
     * DefaultExports.initialize()}.
     *
     * @param node the Hazelcast node instance for accessing cluster and job information
     */
    public static void initTelemetryInstance(@NonNull Node node) {
        NodeExtension nodeExtension = (NodeExtension) node.getNodeExtension();
        ExportsInstanceInitializer.init(node, nodeExtension.getCollectorRegistry());
    }

    /**
     * Checks telemetry configuration and enables JMX if metrics are enabled.
     *
     * <p>When metrics collection is enabled in the SeaTunnel configuration, this method
     * automatically sets the Hazelcast JMX property to "true" to expose Hazelcast internal metrics
     * through JMX. This is required for comprehensive system monitoring.
     *
     * @param seaTunnelConfig the SeaTunnel configuration containing telemetry settings
     * @return {@code true} if metrics are enabled and JMX has been configured, {@code false}
     *     otherwise
     */
    private static boolean checkTelemetryConfig(SeaTunnelConfig seaTunnelConfig) {
        // "hazelcast.jmx" need to set "true", for hazelcast metrics
        if (seaTunnelConfig.getEngineConfig().getTelemetryConfig().getMetric().isEnabled()) {
            seaTunnelConfig
                    .getHazelcastConfig()
                    .getProperties()
                    .setProperty("hazelcast.jmx", "true");
            return true;
        }
        return false;
    }
}
