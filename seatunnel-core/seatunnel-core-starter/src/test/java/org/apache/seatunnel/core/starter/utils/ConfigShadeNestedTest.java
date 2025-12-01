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

package org.apache.seatunnel.core.starter.utils;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigRenderOptions;

import org.apache.seatunnel.core.starter.enums.CryptoMode;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConfigShadeNestedTest {

    private static final ConfigRenderOptions CONFIG_RENDER_OPTIONS =
            ConfigRenderOptions.concise().setFormatted(true);

    @Test
    public void testNestedConfigEncryptionWithExactMatching() throws URISyntaxException {
        URL resource = ConfigShadeNestedTest.class.getResource("/config.nested.conf");
        Assertions.assertNotNull(resource);

        // Load original config
        Config originalConfig = ConfigBuilder.of(Paths.get(resource.toURI()));
        log.info("Original config: {}", originalConfig.root().render(CONFIG_RENDER_OPTIONS));

        // Encrypt (exact matching only - fields must be in sensitive-fields.conf)
        Config encryptedConfig =
                ConfigShadeUtils.encryptConfig("base64", originalConfig, CryptoMode.DEFAULT);
        log.info("Encrypted config: {}", encryptedConfig.root().render(CONFIG_RENDER_OPTIONS));

        // Verify top-level password is encrypted
        String encryptedPassword =
                encryptedConfig.getConfigList("source").get(0).getString("password");
        Assertions.assertNotEquals("my_password", encryptedPassword);

        // Verify nested fields with exact matching are encrypted
        if (encryptedConfig.getConfigList("source").get(0).hasPath("connection_config")) {
            Config connectionConfig =
                    encryptedConfig.getConfigList("source").get(0).getConfig("connection_config");
            if (connectionConfig.hasPath("db_password")) {
                String encryptedDbPassword = connectionConfig.getString("db_password");
                Assertions.assertEquals("nested_password", encryptedDbPassword);
                log.info("Nested db_password encrypted: {}", encryptedDbPassword);
            }
        }

        // Decrypt and verify
        Config decryptedConfig =
                ConfigShadeUtils.decryptConfig("base64", encryptedConfig, CryptoMode.DEFAULT);
        log.info("Decrypted config: {}", decryptedConfig.root().render(CONFIG_RENDER_OPTIONS));

        String decryptedPassword =
                decryptedConfig.getConfigList("source").get(0).getString("password");
        Assertions.assertEquals("my_password", decryptedPassword);
    }

    @Test
    public void testNestedConfigDecryption() throws URISyntaxException {
        URL resource = ConfigShadeNestedTest.class.getResource("/config.nested.conf");
        Assertions.assertNotNull(resource);

        // Load original config
        Config originalConfig = ConfigBuilder.of(Paths.get(resource.toURI()));

        // Encrypt (exact matching only - fields must be in sensitive-fields.conf)
        Config encryptedConfig =
                ConfigShadeUtils.encryptConfig("base64", originalConfig, CryptoMode.DEFAULT);
        log.info("Encrypted config: {}", encryptedConfig.root().render(CONFIG_RENDER_OPTIONS));

        // Verify exact match fields are encrypted
        String encryptedPassword =
                encryptedConfig.getConfigList("source").get(0).getString("password");
        Assertions.assertNotEquals("my_password", encryptedPassword);

        // Verify nested fields with exact matching are encrypted
        Config connectionConfig =
                encryptedConfig.getConfigList("source").get(0).getConfig("connection_config");
        String encryptedDbPassword = connectionConfig.getString("db_password");
        String encryptedApiToken = connectionConfig.getString("api_token");

        Assertions.assertEquals("nested_password", encryptedDbPassword);
        Assertions.assertEquals("my_api_token", encryptedApiToken);

        // Decrypt and verify
        Config decryptedConfig =
                ConfigShadeUtils.decryptConfig("base64", encryptedConfig, CryptoMode.DEFAULT);
        String decryptedPassword =
                decryptedConfig.getConfigList("source").get(0).getString("password");
        Assertions.assertEquals("my_password", decryptedPassword);

        // Verify nested fields are decrypted correctly
        Config decryptedConnectionConfig =
                decryptedConfig.getConfigList("source").get(0).getConfig("connection_config");
        String decryptedDbPassword = decryptedConnectionConfig.getString("db_password");
        String decryptedApiToken = decryptedConnectionConfig.getString("api_token");

        Assertions.assertEquals("nested_password", decryptedDbPassword);
        Assertions.assertEquals("my_api_token", decryptedApiToken);
    }

    @Test
    public void testProgrammaticNestedMapEncryption() {
        // Create a nested configuration programmatically
        Map<String, Object> connectionConfig = new HashMap<>();
        connectionConfig.put("host", "localhost");
        connectionConfig.put("port", 3306);
        connectionConfig.put("db_password", "nested_secret");
        connectionConfig.put("api_token", "my_api_token");
        connectionConfig.put("user_credential", "my_credential");

        Map<String, Object> sourceConfig = new HashMap<>();
        sourceConfig.put("plugin_name", "MySQL");
        sourceConfig.put("username", "root");
        sourceConfig.put("password", "root_password");
        sourceConfig.put("connection_config", connectionConfig);

        Map<String, Object> sinkConfig = new HashMap<>();
        sinkConfig.put("plugin_name", "Console");

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("source", java.util.Collections.singletonList(sourceConfig));
        configMap.put("sink", java.util.Collections.singletonList(sinkConfig));

        Config config = ConfigFactory.parseMap(configMap);
        log.info("Original programmatic config: {}", config.root().render(CONFIG_RENDER_OPTIONS));

        // Encrypt (exact matching only - fields must be in sensitive-fields.conf)
        Config encryptedConfig =
                ConfigShadeUtils.encryptConfig("base64", config, CryptoMode.DEFAULT);

        log.info(
                "Encrypted programmatic config: {}",
                encryptedConfig.root().render(CONFIG_RENDER_OPTIONS));

        // Verify encryption
        String encryptedPassword =
                encryptedConfig.getConfigList("source").get(0).getString("password");
        Assertions.assertNotEquals("root_password", encryptedPassword);

        Config encryptedConnectionConfig =
                encryptedConfig.getConfigList("source").get(0).getConfig("connection_config");
        String encryptedDbPassword = encryptedConnectionConfig.getString("db_password");
        String encryptedApiToken = encryptedConnectionConfig.getString("api_token");
        String encryptedCredential = encryptedConnectionConfig.getString("user_credential");

        Assertions.assertEquals("nested_secret", encryptedDbPassword);
        Assertions.assertEquals("my_api_token", encryptedApiToken);
        Assertions.assertEquals("my_credential", encryptedCredential);

        // Decrypt and verify
        Config decryptedConfig =
                ConfigShadeUtils.decryptConfig("base64", encryptedConfig, CryptoMode.DEFAULT);
        log.info(
                "Decrypted programmatic config: {}",
                decryptedConfig.root().render(CONFIG_RENDER_OPTIONS));

        String decryptedPassword =
                decryptedConfig.getConfigList("source").get(0).getString("password");
        Assertions.assertEquals("root_password", decryptedPassword);

        Config decryptedConnectionConfig =
                decryptedConfig.getConfigList("source").get(0).getConfig("connection_config");
        String decryptedDbPassword = decryptedConnectionConfig.getString("db_password");
        String decryptedApiToken = decryptedConnectionConfig.getString("api_token");
        String decryptedCredential = decryptedConnectionConfig.getString("user_credential");

        Assertions.assertEquals("nested_secret", decryptedDbPassword);
        Assertions.assertEquals("my_api_token", decryptedApiToken);
        Assertions.assertEquals("my_credential", decryptedCredential);
    }
}
