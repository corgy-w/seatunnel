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

package org.apache.seatunnel.connectors.doris.util;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.doris.config.DorisConfig;
import org.apache.seatunnel.connectors.doris.config.DorisOptions;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

class HttpUtilTest {

    @Test
    void shouldUseDefaultConnectTimeoutForSinkHttpClient() throws Exception {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("fenodes", "127.0.0.1:8030");
        configMap.put("username", "root");
        configMap.put("password", "");
        configMap.put("database", "test_db");
        configMap.put("table", "test_table");
        DorisConfig dorisConfig = DorisConfig.of(ReadonlyConfig.fromMap(configMap));

        try (CloseableHttpClient httpClient = new HttpUtil(dorisConfig).getHttpClient()) {
            Assertions.assertEquals(
                    DorisOptions.DORIS_REQUEST_CONNECT_TIMEOUT_MS_DEFAULT,
                    extractDefaultRequestConfig(httpClient).getConnectTimeout());
        }
    }

    private RequestConfig extractDefaultRequestConfig(CloseableHttpClient httpClient)
            throws IllegalAccessException, NoSuchFieldException {
        Field defaultConfigField = findField(httpClient.getClass(), "defaultConfig");
        defaultConfigField.setAccessible(true);
        return (RequestConfig) defaultConfigField.get(httpClient);
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
