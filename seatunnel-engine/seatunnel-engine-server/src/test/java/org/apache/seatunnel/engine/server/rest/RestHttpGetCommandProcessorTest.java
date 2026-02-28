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

package org.apache.seatunnel.engine.server.rest;

import org.apache.seatunnel.engine.common.config.SeaTunnelConfig;
import org.apache.seatunnel.engine.server.AbstractSeaTunnelServerTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class RestHttpGetCommandProcessorTest
        extends AbstractSeaTunnelServerTest<RestHttpGetCommandProcessorTest> {

    @Override
    public SeaTunnelConfig loadSeaTunnelConfig() {
        SeaTunnelConfig config = super.loadSeaTunnelConfig();
        config.getEngineConfig().getTelemetryConfig().getMetric().setEnabled(true);
        return config;
    }

    @Test
    public void testGetMetrics() throws IOException {
        String address = instance.getCluster().getLocalMember().getAddress().getHost();
        int port = instance.getCluster().getLocalMember().getAddress().getPort();
        String urlString = "http://" + address + ":" + port + RestConstant.TELEMETRY_METRICS_URL;

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
                "http://" + address + ":" + port + RestConstant.TELEMETRY_OPEN_METRICS_URL;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        Assertions.assertEquals(200, conn.getResponseCode());

        try (Scanner scanner = new Scanner(conn.getInputStream())) {
            String response = scanner.useDelimiter("\\A").next();
            Assertions.assertTrue(response.contains("jvm_memory_bytes_used"));
            Assertions.assertTrue(response.contains("slot_total"));
            Assertions.assertTrue(response.contains("slot_request_total"));
        }
    }
}
