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

package org.apache.seatunnel.engine.server;

import org.apache.seatunnel.engine.common.exception.SeaTunnelEngineException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.instance.impl.HazelcastInstanceImpl;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetCoordinatorServiceFastFailTest {

    private HazelcastInstanceImpl instance;
    private SeaTunnelServer server;

    @BeforeAll
    public void setUp() {
        instance =
                SeaTunnelServerStarter.createHazelcastInstance(
                        TestUtils.getClusterName("GetCoordinatorServiceFastFailTest"));
        server = instance.node.getNodeEngine().getService(SeaTunnelServer.SERVICE_NAME);
        await().atMost(10000, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            try {
                                Assertions.assertTrue(
                                        server.getCoordinatorService().isCoordinatorActive());
                            } catch (SeaTunnelEngineException e) {
                                Assertions.fail("Coordinator not yet active: " + e.getMessage());
                            }
                        });
        // stop the masterActiveListener scheduler so it won't race with
        // our reflection-based isActive manipulation in tests below
        stopMasterActiveListener(server.getCoordinatorService());
    }

    private void stopMasterActiveListener(CoordinatorService coordinatorService) {
        try {
            Field schedulerField =
                    CoordinatorService.class.getDeclaredField("masterActiveListener");
            schedulerField.setAccessible(true);
            ScheduledExecutorService scheduler =
                    (ScheduledExecutorService) schedulerField.get(coordinatorService);
            scheduler.shutdown();
        } catch (Exception e) {
            Assertions.fail("Failed to stop masterActiveListener: " + e.getMessage());
        }
    }

    @AfterAll
    public void tearDown() {
        if (instance != null) {
            instance.shutdown();
        }
    }

    // Verify getCoordinatorService returns coordinator when active
    @Test
    public void testGetCoordinatorServiceWhenActive() {
        CoordinatorService coordinatorService = server.getCoordinatorService();
        Assertions.assertNotNull(coordinatorService);
        Assertions.assertTrue(coordinatorService.isCoordinatorActive());
    }

    // Verify getCoordinatorService completes within 500ms (not the old 1500ms)
    // when coordinator is not active. The reduced retryPause (100ms * 3 = 300ms)
    // keeps total sleep well below the old 500ms * 3 = 1500ms.
    @Test
    public void testGetCoordinatorServiceReducedSleepTime() throws Exception {
        Field isActiveField = CoordinatorService.class.getDeclaredField("isActive");
        isActiveField.setAccessible(true);

        CoordinatorService coordinatorService = server.getCoordinatorService();

        try {
            isActiveField.setBoolean(coordinatorService, false);

            long startTime = System.currentTimeMillis();
            try {
                server.getCoordinatorService();
                Assertions.fail("Should throw when coordinator is not active");
            } catch (SeaTunnelEngineException e) {
                // expected - either RetryableException or SeaTunnelEngineException
            }
            long elapsed = System.currentTimeMillis() - startTime;

            // old code: sleep 500ms * 3 = 1500ms minimum
            // new code: sleep 100ms * 3 = 300ms maximum
            Assertions.assertTrue(
                    elapsed < 500,
                    "getCoordinatorService() should complete within 500ms (100ms * 3 retries), "
                            + "but took "
                            + elapsed
                            + "ms");
        } finally {
            isActiveField.setBoolean(coordinatorService, true);
        }
    }
}
