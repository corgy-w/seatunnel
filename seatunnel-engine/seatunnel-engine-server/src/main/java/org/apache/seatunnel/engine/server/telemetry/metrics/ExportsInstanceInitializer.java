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

package org.apache.seatunnel.engine.server.telemetry.metrics;

import org.apache.seatunnel.engine.server.telemetry.metrics.exports.ClusterMetricExports;
import org.apache.seatunnel.engine.server.telemetry.metrics.exports.JobMetricExports;
import org.apache.seatunnel.engine.server.telemetry.metrics.exports.JobThreadPoolStatusExports;
import org.apache.seatunnel.engine.server.telemetry.metrics.exports.NodeMetricExports;

import com.hazelcast.instance.impl.Node;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;

/**
 * Initialize and register Prometheus metric exporters.
 *
 * <p>This initializer wires SeaTunnel Engine custom exporters and JVM/Process default exports.
 */
public final class ExportsInstanceInitializer {

    private ExportsInstanceInitializer() {}

    public static void init(Node node, CollectorRegistry collectorRegistry) {
        if (node == null || collectorRegistry == null) {
            return;
        }
        try {
            collectorRegistry.clear();
            DefaultExports.register(collectorRegistry);
            new JobMetricExports(node).register(collectorRegistry);
            new JobThreadPoolStatusExports(node).register(collectorRegistry);
            new NodeMetricExports(node).register(collectorRegistry);
            new ClusterMetricExports(node).register(collectorRegistry);
            node.getLogger(ExportsInstanceInitializer.class)
                    .info("Prometheus metrics collectors initialized successfully");
        } catch (Exception e) {
            node.getLogger(ExportsInstanceInitializer.class)
                    .warning("Failed to initialize Prometheus metrics collectors", e);
        }
    }
}
