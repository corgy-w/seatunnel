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

import org.apache.seatunnel.engine.server.CoordinatorService;
import org.apache.seatunnel.engine.server.SeaTunnelServer;

import com.google.common.collect.Lists;
import com.hazelcast.cluster.impl.MemberImpl;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.cluster.ClusterService;
import com.hazelcast.internal.jmx.ManagementService;
import com.hazelcast.logging.ILogger;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * AbstractCollector is the base class for all Prometheus metric collectors in the SeaTunnel server.
 * It provides common functionality and utilities for collecting and exporting metrics.
 */
public abstract class AbstractCollector extends Collector {

    protected static String CLUSTER = "cluster";
    protected static String ADDRESS = "address";

    protected Node node;

    public AbstractCollector(final Node node) {
        this.node = node;
    }

    /**
     * Returns the underlying Hazelcast {@link Node} instance used to access cluster/runtime
     * services.
     */
    protected Node getNode() {
        return node;
    }

    /**
     * Returns a Hazelcast logger associated with the given class.
     *
     * <p>All metric exporters should use this helper so logs are consistent with the Hazelcast
     * logging subsystem.
     */
    protected ILogger getLogger(Class clazz) {
        return getNode().getLogger(clazz);
    }

    /**
     * Whether the current node is the master node.
     *
     * <p>Some metrics are only collected on the master (for example, cluster-level job counters) to
     * avoid duplicated time series and ambiguous semantics.
     */
    protected boolean isMaster() {
        return getNode().isMaster();
    }

    /**
     * Returns the local Hazelcast member object.
     *
     * <p>This is commonly used to derive stable labels such as {@code address}.
     */
    protected MemberImpl getLocalMember() {
        return getNode().nodeEngine.getLocalMember();
    }

    /**
     * Returns the {@link SeaTunnelServer} service from the Hazelcast node engine.
     *
     * @return SeaTunnelServer service instance
     */
    protected SeaTunnelServer getServer() {
        return getNode().getNodeEngine().getService(SeaTunnelServer.SERVICE_NAME);
    }

    /**
     * Returns the {@link CoordinatorService} from {@link SeaTunnelServer}.
     *
     * @return CoordinatorService instance
     */
    protected CoordinatorService getCoordinatorService() {
        return getServer().getCoordinatorService();
    }

    /**
     * Returns Hazelcast {@link ManagementService} used by some exporters to access MBeans.
     *
     * @return Hazelcast ManagementService instance
     */
    protected ManagementService getManagementService() {
        return getNode().hazelcastInstance.getManagementService();
    }

    /**
     * Returns Hazelcast {@link ClusterService} for cluster-level information (e.g., members,
     * master, cluster time).
     */
    protected ClusterService getClusterService() {
        return getNode().getClusterService();
    }

    /**
     * Returns the local member address in the {@code host:port} form.
     *
     * <p>This value is used as the {@code address} label for node-scoped metrics.
     */
    protected String localAddress() {
        // Prefer Hazelcast node address directly to avoid DNS resolution issues in container/K8s
        // environments. This address should match the member address used by the cluster.
        return getNode().getThisAddress().getHost() + ":" + getNode().getThisAddress().getPort();
    }

    /**
     * Returns the master member address in the {@code host:port} form.
     *
     * @throws UnknownHostException If Hazelcast returns a master address that cannot be resolved to
     *     a host address.
     */
    protected String masterAddress() throws UnknownHostException {
        if (getNode().getMasterAddress() == null) {
            throw new UnknownHostException("Master address is null");
        }
        return getNode().getMasterAddress().getHost()
                + ":"
                + getNode().getMasterAddress().getPort();
    }

    /**
     * Returns Hazelcast cluster name.
     *
     * <p>This value is used as the {@code cluster} label for all SeaTunnel Engine custom metrics.
     */
    protected String getClusterName() {
        return getNode().getConfig().getClusterName();
    }

    /**
     * Builds label values for a metric sample.
     *
     * <p>The returned list always starts with the {@code cluster} label value (cluster name), then
     * appends any additional values in order. Callers must ensure the order matches the label names
     * defined by {@link #clusterLabelNames(String...)}.
     */
    protected List<String> labelValues(String... values) {
        List<String> labelValues = new ArrayList<>();
        labelValues.add(getClusterName());
        if (values != null) {
            labelValues.addAll(Lists.newArrayList(values));
        }
        return labelValues;
    }

    /**
     * Builds label name list for SeaTunnel Engine custom metrics.
     *
     * <p>The returned list always starts with {@code cluster}, then appends any additional labels
     * in order. Callers should use {@link #labelValues(String...)} to build the corresponding
     * values.
     */
    protected List<String> clusterLabelNames(String... labels) {
        List<String> labelNames = new ArrayList<>();
        labelNames.add(CLUSTER);
        if (labels != null) {
            labelNames.addAll(Lists.newArrayList(labels));
        }
        return labelNames;
    }

    /**
     * Adds a long value sample to a {@link GaugeMetricFamily}.
     *
     * <p>Prometheus gauge samples are stored as {@code double}; using this helper avoids repeated
     * casts in exporters.
     */
    protected void longMetric(
            GaugeMetricFamily metricFamily, long count, List<String> labelValues) {
        metricFamily.addMetric(labelValues, count);
    }

    /**
     * Adds an int value sample to a {@link GaugeMetricFamily}.
     *
     * @param metricFamily target metric family
     * @param count sample value
     * @param labelValues label values
     */
    protected void intMetric(GaugeMetricFamily metricFamily, int count, List<String> labelValues) {
        metricFamily.addMetric(labelValues, count);
    }
}
