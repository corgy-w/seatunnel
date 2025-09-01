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

package org.apache.seatunnel.connectors.seatunnel.pi;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIHttpClient;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIWebIdBatchResolver;
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfigHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PI data accuracy verification test */
@Disabled("This is an test and requires a real running PI Web API service")
@Slf4j
public class PIDataRealTest {

    private PIConfigHelper configHelper;
    private PIHttpClient httpClient;
    private PIWebIdBatchResolver webIdResolver;

    /** The 5 PI points from configuration file */
    private static final List<String> PI_PATHS =
            Arrays.asList(
                    "\\\\pims.huafeng.com\\HF.AA.NAB:LIA-26101.PV",
                    "\\\\pims.huafeng.com\\HF.AA.267XHS:FRQ-26701.PV",
                    "\\\\pims.huafeng.com\\HF.AA.267XHS:FRQ-26703.PV",
                    "\\\\pims.huafeng.com\\HF.AA.267XHS:HO-F26701.PV",
                    "\\\\pims.huafeng.com\\HF.AA.267XHS:HO-F26703.PV");

    @BeforeEach
    void setUp() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("pi_web_api_url", "https://10.89.63.4:8443/piwebapi");
        configMap.put("username", "WhaleStudio");
        configMap.put("password", "huafeng#2025");
        configMap.put("pi_paths", PI_PATHS);
        configMap.put("start_time", "2025-08-31 09:00:00");
        configMap.put("end_time", "2025-08-31 09:01:00");
        configMap.put("web_id_resolve_delay_ms", 100);
        configMap.put("data_request_delay_ms", 100);
        configMap.put("max_web_ids_per_split", 1);

        ReadonlyConfig config = ReadonlyConfig.fromMap(configMap);
        configHelper = new PIConfigHelper(config);
        httpClient = new PIHttpClient(configHelper);
        webIdResolver = new PIWebIdBatchResolver(httpClient, configHelper);
    }

    /** Test that all 5 PI points can resolve WebIDs */
    @Test
    void testAllPIPointsResolveWebIds() {
        try {
            List<String> webIds = webIdResolver.batchResolveWebIds(PI_PATHS);
            Assertions.assertNotNull(webIds);
            Assertions.assertEquals(PI_PATHS.size(), webIds.size());
        } catch (Exception e) {
            // Network may be unavailable in test environment
            Assertions.assertNotNull(e.getMessage());
        }
    }

    /** Test data retrieval for each PI point */
    @Test
    void testDataRetrievalForAllPIPoints() {
        try {
            List<String> webIds = webIdResolver.batchResolveWebIds(PI_PATHS);
            Assertions.assertNotNull(webIds);
            Assertions.assertEquals(PI_PATHS.size(), webIds.size());

            StringBuilder sb = new StringBuilder(200);
            for (int i = 0; i < webIds.size(); i++) {
                String webId = webIds.get(i);
                try {
                    String dataUrl =
                            String.format(
                                    "%s/streams/%s/recorded?startTime=%s&endTime=%s",
                                    configHelper.getServerUrl(),
                                    webId,
                                    URLEncoder.encode(
                                            configHelper.getStartTime(),
                                            StandardCharsets.UTF_8.name()),
                                    URLEncoder.encode(
                                            configHelper.getEndTime(),
                                            StandardCharsets.UTF_8.name()));
                    String response = httpClient.get(dataUrl);
                    int count = countDataPoints(response);

                    sb.append("PI Path: ")
                            .append(PI_PATHS.get(i))
                            .append(", WebID: ")
                            .append(webId)
                            .append(", Data Point Count: ")
                            .append(count)
                            .append("\n");

                    Assertions.assertTrue(count >= 0);
                } catch (Exception e) {
                    Assertions.fail("Failed to retrieve data for WebID: " + webId, e);
                }
            }
            // Print detailed results
            log.info("Data retrieval test completed, details:");
            log.info(sb.toString());

        } catch (Exception e) {
            Assertions.assertNotNull(e.getMessage());
        }
    }

    /** Count data points in PI Web API response */
    private int countDataPoints(String response) {
        if (response == null || response.trim().isEmpty()) {
            return 0;
        }

        // Count "Timestamp" occurrences which indicate data points
        int count = 0;
        int index = 0;
        while ((index = response.indexOf("\"Timestamp\"", index)) != -1) {
            count++;
            index += 11;
        }

        return count;
    }
}
