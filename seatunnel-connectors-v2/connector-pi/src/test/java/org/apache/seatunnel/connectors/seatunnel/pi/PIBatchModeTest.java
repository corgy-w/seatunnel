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
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfig;
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfigHelper;
import org.apache.seatunnel.connectors.seatunnel.pi.utils.PIPathValidator;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PI batch mode test */
public class PIBatchModeTest {

    @Test
    public void testPIConfigHelperBatchMode() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put(
                PIConfig.PI_WEB_API_URL.key(),
                "https://10.89.63.4:8443/piwebapi/streamsets/recorded");
        configMap.put(PIConfig.USERNAME.key(), "WhaleStudio");
        configMap.put(PIConfig.PASSWORD.key(), "huafeng#2025");
        configMap.put(PIConfig.AUTH_TYPE.key(), "Basic");
        configMap.put(PIConfig.START_TIME.key(), "2024-06-20 09:00:00");
        configMap.put(PIConfig.END_TIME.key(), "2024-06-20 09:01:00");

        Map<String, String> jsonFieldMap = new HashMap<>();
        jsonFieldMap.put("webId", "$.WebId");
        jsonFieldMap.put("name", "$.Name");
        jsonFieldMap.put("timestamp", "$.Items[*].Timestamp");
        jsonFieldMap.put("value", "$.Items[*].Value");
        jsonFieldMap.put("good", "$.Items[*].Good");
        configMap.put(PIConfig.JSON_FIELD.key(), jsonFieldMap);

        List<String> piPaths =
                Arrays.asList(
                        "\\\\PI-AFServer01&02\\WebAPI\\Test|Attribute1",
                        "\\\\PI-AFServer01&02\\WebAPI\\Test|Attribute2");
        configMap.put(PIConfig.PI_PATHS.key(), piPaths);

        ReadonlyConfig config = ReadonlyConfig.fromMap(configMap);
        PIConfigHelper piConfigHelper = new PIConfigHelper(config);

        assertNotNull(piConfigHelper.getStartTime());
        assertNotNull(piConfigHelper.getEndTime());
        assertNotNull(piConfigHelper.getJsonField());
        assertEquals(2, piConfigHelper.getPiPaths().size());
        assertEquals(10, piConfigHelper.getWebIdsPerSplit()); // Default value from PIConfig
    }

    @Test
    public void testPIPathValidator() {
        List<String> validPaths =
                Arrays.asList(
                        "\\\\PI-AFServer01&02\\WebAPI\\Test|Attribute1",
                        "\\\\pims.huafeng.com\\HF.AA.NAB:LIA-26101.PV");

        // Test validation pass case
        PIPathValidator.validatePiPaths(validPaths);

        // Test duplicate path validation case
        List<String> duplicatePaths =
                Arrays.asList(
                        "\\\\PI-AFServer01&02\\WebAPI\\Test|Attribute1",
                        "\\\\PI-AFServer01&02\\WebAPI\\Test|Attribute1");

        assertThrows(
                Exception.class,
                () -> {
                    PIPathValidator.validatePiPaths(duplicatePaths);
                });
    }

    @Test
    public void testTimeFormatSupport() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put(
                PIConfig.PI_WEB_API_URL.key(),
                "https://10.89.63.4:8443/piwebapi/streamsets/recorded");
        configMap.put(PIConfig.USERNAME.key(), "WhaleStudio");
        configMap.put(PIConfig.PASSWORD.key(), "huafeng#2025");
        configMap.put(PIConfig.AUTH_TYPE.key(), "Basic");

        // Test space-separated time format
        configMap.put(PIConfig.START_TIME.key(), "2024-06-20 09:00:00");

        Map<String, String> jsonFieldMap = new HashMap<>();
        jsonFieldMap.put("webId", "$.WebId");
        configMap.put(PIConfig.JSON_FIELD.key(), jsonFieldMap);

        List<String> piPaths = Arrays.asList("\\\\PI-AFServer01&02\\WebAPI\\Test|Attribute1");
        configMap.put(PIConfig.PI_PATHS.key(), piPaths);

        ReadonlyConfig config = ReadonlyConfig.fromMap(configMap);
        PIConfigHelper piConfigHelper = new PIConfigHelper(config);

        assertNotNull(piConfigHelper.getStartTime());
        // PIConfigHelper doesn't have getEndDateTime method, so we test the basic functionality
        assertNotNull(piConfigHelper.getJsonField());
        assertEquals(1, piConfigHelper.getPiPaths().size());
    }
}
