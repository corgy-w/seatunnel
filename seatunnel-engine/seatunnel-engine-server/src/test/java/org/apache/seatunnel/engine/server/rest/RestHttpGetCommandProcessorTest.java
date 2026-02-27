package org.apache.seatunnel.engine.server.rest;

import org.apache.seatunnel.engine.common.config.SeaTunnelConfig;
import org.apache.seatunnel.engine.server.AbstractSeaTunnelServerTest;
import org.apache.seatunnel.engine.server.SeaTunnelServer;
import org.apache.seatunnel.engine.server.SeaTunnelServerStarter;
import org.apache.seatunnel.engine.server.TestUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class RestHttpGetCommandProcessorTest
        extends AbstractSeaTunnelServerTest<RestHttpGetCommandProcessorTest> {

    @Override
    @BeforeAll
    public void before() {
        String name = ((RestHttpGetCommandProcessorTest) this).getClass().getName();
        String yaml =
                "hazelcast:\n"
                        + "  cluster-name: seatunnel\n"
                        + "  network:\n"
                        + "    rest-api:\n"
                        + "      enabled: true\n"
                        + "      endpoint-groups:\n"
                        + "        CLUSTER_WRITE:\n"
                        + "          enabled: true\n"
                        + "    join:\n"
                        + "      tcp-ip:\n"
                        + "        enabled: true\n"
                        + "        member-list:\n"
                        + "          - localhost\n"
                        + "    port:\n"
                        + "      auto-increment: true\n"
                        + "      port-count: 100\n"
                        + "      port: 5801\n"
                        + "\n"
                        + "  properties:\n"
                        + "    hazelcast.invocation.max.retry.count: 200\n"
                        + "    hazelcast.tcp.join.port.try.count: 30\n"
                        + "    hazelcast.invocation.retry.pause.millis: 2000\n"
                        + "    hazelcast.slow.operation.detector.stacktrace.logging.enabled: true\n"
                        + "    hazelcast.logging.type: log4j2\n"
                        + "    hazelcast.operation.generic.thread.count: 200\n";
        Config hazelcastConfig = Config.loadFromString(yaml);
        hazelcastConfig.setClusterName(
                TestUtils.getClusterName("AbstractSeaTunnelServerTest_" + name));
        SeaTunnelConfig seaTunnelConfig = loadSeaTunnelConfig();
        seaTunnelConfig.setHazelcastConfig(hazelcastConfig);
        seaTunnelConfig.getEngineConfig().getTelemetryConfig().getMetric().setEnabled(true);
        instance = SeaTunnelServerStarter.createHazelcastInstance(seaTunnelConfig);
        nodeEngine = instance.node.nodeEngine;
        server = nodeEngine.getService(SeaTunnelServer.SERVICE_NAME);
        LOGGER = nodeEngine.getLogger(AbstractSeaTunnelServerTest.class);
    }

    @Test
    public void testGetMetrics() throws IOException {
        String address = instance.getCluster().getLocalMember().getAddress().getHost();
        int port = instance.getCluster().getLocalMember().getAddress().getPort();
        String urlString = "http://" + address + ":" + port + "/hazelcast/rest/instance/metrics";

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        Assertions.assertEquals(200, conn.getResponseCode());

        try (Scanner scanner = new Scanner(conn.getInputStream())) {
            String response = scanner.useDelimiter("\\A").next();
            Assertions.assertTrue(response.contains("jvm_memory_bytes_used"));
            Assertions.assertTrue(response.contains("# TYPE jvm_memory_bytes_used gauge"));
            Assertions.assertTrue(response.contains("slot_total"));
            Assertions.assertTrue(response.contains("slot_request_total"));
        }
    }

    @Test
    public void testGetOpenMetrics() throws IOException {
        String address = instance.getCluster().getLocalMember().getAddress().getHost();
        int port = instance.getCluster().getLocalMember().getAddress().getPort();
        String urlString =
                "http://" + address + ":" + port + "/hazelcast/rest/instance/openmetrics";

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        Assertions.assertEquals(200, conn.getResponseCode());

        try (Scanner scanner = new Scanner(conn.getInputStream())) {
            String response = scanner.useDelimiter("\\A").next();
            Assertions.assertTrue(response.contains("jvm_memory_bytes_used"));
            // OpenMetrics format might be slightly different or include specific headers,
            // but for now checking content presence is a good start.
            Assertions.assertTrue(response.contains("slot_total"));
            Assertions.assertTrue(response.contains("slot_request_total"));
        }
    }
}
