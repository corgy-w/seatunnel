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
import org.apache.seatunnel.engine.server.license.LicenseDelegator;
import org.apache.seatunnel.engine.server.resourcemanager.worker.WorkerProfile;

import org.apache.commons.collections4.MapUtils;

import org.whaleops.license.dto.LicenseInfo;
import org.whaleops.license.enums.LicenseNodeEnum;
import org.whaleops.license.utils.LicenseUtil;

import com.hazelcast.cluster.Address;
import org.apache.seatunnel.engine.common.config.EngineConfig;

import com.hazelcast.spi.impl.NodeEngine;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
public class StandaloneResourceManager extends AbstractResourceManager {

    private final EngineConfig engineConfig;

    private final LicenseDelegator licenseDelegator;

    public StandaloneResourceManager(NodeEngine nodeEngine, EngineConfig engineConfig) {
        super(nodeEngine, engineConfig);
        this.engineConfig = engineConfig;
        this.licenseDelegator = new LicenseDelegator(engineConfig);
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

    public LicenseDelegator getLicenseDelegator() {
        return licenseDelegator;
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck(String ip) {
        if (!LicenseUtil.isNeedCheckLicense()) {
            return Boolean.TRUE;
        }
        try {
            final LicenseInfo licenseInfo = licenseDelegator.getSystemLicense();
            final Set<String> activeHostIps =
                    registerWorker.keySet().stream()
                            .map(Address::getHost)
                            .collect(Collectors.toSet());
            if (LicenseUtil.checkLicenseServer(
                    licenseInfo, activeHostIps, ip, LicenseNodeEnum.WT)) {
                return true;
            }
            // for test
            if (LicenseUtil.checkLicenseServer(
                    licenseInfo, activeHostIps, "127.0.0.1", LicenseNodeEnum.WT)) {
                return true;
            }
            licenseDelegator.markLicenseInvalidated();
            return false;
        } catch (Exception ex) {
            licenseDelegator.markLicenseInvalidated();
            log.error("Check license failed", ex);
            return false;
        }
    }
}
