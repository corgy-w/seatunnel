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

package org.apache.seatunnel.engine.server.resourcemanager;

import org.apache.seatunnel.engine.common.config.EngineConfig;
import org.apache.seatunnel.engine.server.license.WhaleTunnelLicenseBillingServiceImpl;
import org.apache.seatunnel.engine.server.license.WhaleTunnelLicenseServiceImpl;
import org.apache.seatunnel.engine.server.resourcemanager.worker.WorkerProfile;

import org.apache.commons.collections4.MapUtils;

import org.whaleops.license.LicenseManager;
import org.whaleops.license.dto.LicenseInfo;
import org.whaleops.license.enums.LicenseNodeEnum;
import org.whaleops.license.utils.LicenseUtil;

import com.hazelcast.cluster.Address;
import com.hazelcast.spi.impl.NodeEngine;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class StandaloneResourceManager extends AbstractResourceManager {

    private final EngineConfig engineConfig;

    public StandaloneResourceManager(NodeEngine nodeEngine, EngineConfig engineConfig) {
        super(nodeEngine);
        this.engineConfig = engineConfig;
    }

    protected ConcurrentMap<Address, WorkerProfile> filterRegisterWorker(
            ConcurrentMap<Address, WorkerProfile> registerWorker) {
        if (MapUtils.isEmpty(registerWorker)) {
            return registerWorker;
        }
        ConcurrentMap<Address, WorkerProfile> afterFilteringRegisterWorker =
                new ConcurrentHashMap<>();
        registerWorker.forEach(
                (address, workerProfile) -> {
                    if (isPassedLicenseCheck(address.getHost())) {
                        afterFilteringRegisterWorker.put(address, workerProfile);
                    }
                });
        return afterFilteringRegisterWorker;
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck(String ip) {
        LicenseManager licenseManager = new LicenseManager();
        Class<?> clazz = licenseManager.getClass();
        Field licenseServiceField = clazz.getDeclaredField("licenseService");
        licenseServiceField.setAccessible(true);
        licenseServiceField.set(licenseManager, new WhaleTunnelLicenseServiceImpl(engineConfig));

        Field licenseBillingServiceField = clazz.getDeclaredField("licenseBillingService");
        licenseBillingServiceField.setAccessible(true);
        licenseBillingServiceField.set(licenseManager, new WhaleTunnelLicenseBillingServiceImpl());

        licenseManager.refreshLicenseCache();
        final LicenseInfo latestValidLicenseInfo = licenseManager.getLatestValidLicenseInfo();
        if (!LicenseUtil.checkLicenseStartAndEndTime(latestValidLicenseInfo)) {
            return false;
        }
        if (LicenseUtil.checkLicenseServer(
                latestValidLicenseInfo, new HashSet<>(), ip, LicenseNodeEnum.WT)) {
            return true;
        }
        // for test
        if (LicenseUtil.checkLicenseServer(
                latestValidLicenseInfo, new HashSet<>(), "127.0.0.1", LicenseNodeEnum.WT)) {
            return true;
        }
        return false;
    }
}
