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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

public class Gbase8aVcAwareConnectionHelperTest {

    @Test
    void shouldExtractVcNameAndTargetDatabase() {
        String url = "jdbc:gbase://127.0.0.1:5258/rot_vcup2_db?vcName=vcup2";

        Assertions.assertEquals("vcup2", Gbase8aVcAwareConnectionHelper.extractVcName(url).get());
        Assertions.assertEquals(
                "rot_vcup2_db",
                Gbase8aVcAwareConnectionHelper.extractTargetDatabase(url).orElse(null));
    }

    @Test
    void shouldBuildBootstrapUrlsForBusinessDatabase() {
        String url = "jdbc:gbase://127.0.0.1:5258/rot_vcup2_db?vcName=vcup2";

        Assertions.assertEquals(
                Arrays.asList(
                        "jdbc:gbase://127.0.0.1:5258/information_schema?vcName=vcup2",
                        "jdbc:gbase://127.0.0.1:5258/gbase?vcName=vcup2"),
                Gbase8aVcAwareConnectionHelper.buildBootstrapUrls(url));
    }

    @Test
    void shouldKeepBootstrapDatabaseUrl() {
        String url = "jdbc:gbase://127.0.0.1:5258/information_schema?vcName=vcup2";

        Assertions.assertEquals(
                Collections.singletonList(url),
                Gbase8aVcAwareConnectionHelper.buildBootstrapUrls(url));
    }
}
