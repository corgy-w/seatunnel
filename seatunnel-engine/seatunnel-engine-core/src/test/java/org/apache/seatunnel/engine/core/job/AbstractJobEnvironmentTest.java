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

package org.apache.seatunnel.engine.core.job;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.engine.common.config.JobConfig;
import org.apache.seatunnel.engine.core.dag.logical.LogicalDag;
import org.apache.seatunnel.engine.core.parse.MultipleTableJobConfigParser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AbstractJobEnvironmentTest {

    @Test
    public void testAddCommonPluginJarsFromEnvOptions() throws Exception {
        Path jarPath = Files.createTempFile("seatunnel-custom-udf", ".jar");
        try {
            TestingJobEnvironment jobEnvironment = new TestingJobEnvironment();
            ReadonlyConfig envOptions =
                    ReadonlyConfig.fromMap(
                            Collections.singletonMap("jars", jarPath.toUri().toString()));

            jobEnvironment.addEnvOptions(envOptions);
            jobEnvironment.addEnvOptions(envOptions);

            List<URL> commonPluginJars = jobEnvironment.getCommonPluginJars();
            Assertions.assertEquals(1, commonPluginJars.size());
            Assertions.assertEquals(jarPath.toUri().toURL(), commonPluginJars.get(0));
        } finally {
            Files.deleteIfExists(jarPath);
        }
    }

    private static final class TestingJobEnvironment extends AbstractJobEnvironment {

        private TestingJobEnvironment() {
            super(new JobConfig(), false);
        }

        private void addEnvOptions(ReadonlyConfig envOptions) {
            addCommonPluginJarsFromEnvOptions(envOptions);
        }

        private List<URL> getCommonPluginJars() {
            return commonPluginJars;
        }

        @Override
        protected Set<URL> searchPluginJars() {
            return Collections.emptySet();
        }

        @Override
        protected MultipleTableJobConfigParser getJobConfigParser() {
            return null;
        }

        @Override
        protected LogicalDag getLogicalDag() {
            return null;
        }
    }
}
