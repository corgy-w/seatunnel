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

package org.apache.seatunnel.connectors.seatunnel.pi.source;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.table.connector.TableSource;
import org.apache.seatunnel.api.table.factory.TableSourceFactoryContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** PISourceFactory unit test */
public class PISourceFactoryTest {

    private PISourceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PISourceFactory();
    }

    @Test
    void testFactoryIdentifier() {
        Assertions.assertEquals("PI", factory.factoryIdentifier());
    }

    @Test
    void testOptionRuleNotThrowException() {
        // Test that OptionRule construction doesn't throw duplicate configuration exception
        Assertions.assertDoesNotThrow(
                () -> {
                    OptionRule optionRule = factory.optionRule();
                    Assertions.assertNotNull(optionRule);
                });
    }

    @Test
    void testGetSourceClass() {
        Assertions.assertEquals(PISource.class, factory.getSourceClass());
    }

    @Test
    void testCreateSourceWithValidConfig() {
        // Prepare valid configuration
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("pi_web_api_url", "https://test.pi.server:8443/piwebapi");
        configMap.put("read_mode", "REALTIME");
        configMap.put(
                "pi_tag_paths", Arrays.asList("\\\\\\\\server\\\\tag1", "\\\\\\\\server\\\\tag2"));

        // Create Schema configuration
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> fields = new HashMap<>();
        Map<String, String> field1 = new HashMap<>();
        field1.put("type", "string");
        fields.put("tag_name", field1);
        Map<String, String> field2 = new HashMap<>();
        field2.put("type", "double");
        fields.put("value", field2);
        Map<String, String> field3 = new HashMap<>();
        field3.put("type", "timestamp");
        fields.put("timestamp", field3);
        schema.put("fields", fields);
        configMap.put("schema", schema);

        // Add authentication configuration
        configMap.put("username", "testuser");
        configMap.put("password", "testpass");

        // Add REALTIME mode conditional configuration
        configMap.put("include_initial_values", true);

        ReadonlyConfig config = ReadonlyConfig.fromMap(configMap);
        TableSourceFactoryContext context =
                new TableSourceFactoryContext(
                        config, Thread.currentThread().getContextClassLoader());

        // Test that creating Source doesn't throw exception
        Assertions.assertDoesNotThrow(
                () -> {
                    Assertions.assertNotNull(factory.createSource(context));
                });
    }

    @Test
    void testCreateSourceWithBatchConfig() {
        // Prepare batch mode configuration
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("pi_web_api_url", "https://test.pi.server:8443/piwebapi");
        configMap.put("read_mode", "BATCH");
        configMap.put("pi_paths", Arrays.asList("\\\\\\\\server\\\\tag1"));

        // Create Schema configuration
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> fields = new HashMap<>();
        Map<String, String> field1 = new HashMap<>();
        field1.put("type", "string");
        fields.put("tag_name", field1);
        schema.put("fields", fields);
        configMap.put("schema", schema);

        // Add batch mode conditional configuration
        configMap.put("start_time", "2024-01-01T00:00:00Z");
        configMap.put("end_time", "2024-01-02T00:00:00Z");

        ReadonlyConfig config = ReadonlyConfig.fromMap(configMap);
        TableSourceFactoryContext context =
                new TableSourceFactoryContext(
                        config, Thread.currentThread().getContextClassLoader());

        // Test that creating Source doesn't throw exception
        Assertions.assertDoesNotThrow(
                () -> {
                    TableSource source = factory.createSource(context);
                    Assertions.assertNotNull(source);
                });
    }

    @Test
    void testCreateSourceWithIncrementalConfig() {
        // Prepare incremental mode configuration
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("pi_web_api_url", "https://test.pi.server:8443/piwebapi");
        configMap.put("read_mode", "INCREMENTAL");
        configMap.put("pi_tag_paths", Arrays.asList("\\\\\\\\server\\\\tag1"));

        // Create Schema configuration
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> fields = new HashMap<>();
        Map<String, String> field1 = new HashMap<>();
        field1.put("type", "string");
        fields.put("tag_name", field1);
        schema.put("fields", fields);
        configMap.put("schema", schema);

        // Add incremental mode conditional configuration
        configMap.put("query_window_minutes", 10);
        configMap.put("overlap_minutes", 2);

        ReadonlyConfig config = ReadonlyConfig.fromMap(configMap);
        TableSourceFactoryContext context =
                new TableSourceFactoryContext(
                        config, Thread.currentThread().getContextClassLoader());

        // Test that creating Source doesn't throw exception
        Assertions.assertDoesNotThrow(
                () -> {
                    TableSource source = factory.createSource(context);
                    Assertions.assertNotNull(source);
                });
    }
}
