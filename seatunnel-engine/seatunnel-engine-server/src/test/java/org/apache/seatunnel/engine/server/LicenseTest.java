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

import org.apache.seatunnel.engine.common.config.EngineConfig;
import org.apache.seatunnel.engine.server.license.WhaleTunnelLicenseServiceImpl;
import org.apache.seatunnel.engine.server.utils.NetUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.whaleops.license.LicenseManager;
import org.whaleops.license.dto.LicenseInfo;
import org.whaleops.license.utils.LicenseUtil;

import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;

public class LicenseTest {

    @Test
    public void testLicense() {
        Assertions.assertTrue(isPassedLicenseCheck());
        Assertions.assertTrue(isPassedLicenseCheck2());
        Assertions.assertTrue(isPassedLicenseCheck3());
        Assertions.assertTrue(isPassedLicenseCheck4());
    }

    @Test
    public void testIP() {
        System.out.println(NetUtils.getAllIp());
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck() {
        LicenseManager licenseManager = new LicenseManager();
        Class<?> clazz = licenseManager.getClass();
        Field nameField = clazz.getDeclaredField("licenseService");
        nameField.setAccessible(true);
        nameField.set(licenseManager, new WhaleTunnelLicenseServiceImpl(getEngineConfig()));

        licenseManager.init();
        final LicenseInfo latestValidLicenseInfo = licenseManager.getLatestValidLicenseInfo();
        if (!LicenseUtil.checkLicenseStartAndEndTime(latestValidLicenseInfo)) {
            return false;
        }
        return LicenseUtil.checkLicenseServer(
                latestValidLicenseInfo, new HashSet<>(), "172.18.22.204");
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck2() {
        LicenseManager licenseManager = new LicenseManager();
        Class<?> clazz = licenseManager.getClass();
        Field nameField = clazz.getDeclaredField("licenseService");
        nameField.setAccessible(true);
        nameField.set(licenseManager, new WhaleTunnelLicenseServiceImpl(getEngineConfig()));

        licenseManager.init();
        final LicenseInfo latestValidLicenseInfo = licenseManager.getLatestValidLicenseInfo();
        if (!LicenseUtil.checkLicenseStartAndEndTime(latestValidLicenseInfo)) {
            return false;
        }
        return LicenseUtil.checkLicenseServer(
                latestValidLicenseInfo, new HashSet<>(), "172.18.22.207");
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck3() {
        LicenseManager licenseManager = new LicenseManager();
        Class<?> clazz = licenseManager.getClass();
        Field nameField = clazz.getDeclaredField("licenseService");
        nameField.setAccessible(true);
        nameField.set(licenseManager, new WhaleTunnelLicenseServiceImpl(getEngineConfig()));

        licenseManager.init();
        final LicenseInfo latestValidLicenseInfo = licenseManager.getLatestValidLicenseInfo();
        if (!LicenseUtil.checkLicenseStartAndEndTime(latestValidLicenseInfo)) {
            return false;
        }
        return LicenseUtil.checkLicenseServer(latestValidLicenseInfo, new HashSet<>(), "127.0.0.1");
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck4() {
        LicenseManager licenseManager = new LicenseManager();
        Class<?> clazz = licenseManager.getClass();
        Field nameField = clazz.getDeclaredField("licenseService");
        nameField.setAccessible(true);
        nameField.set(licenseManager, new WhaleTunnelLicenseServiceImpl(getEngineConfig()));

        licenseManager.init();
        final LicenseInfo latestValidLicenseInfo = licenseManager.getLatestValidLicenseInfo();
        if (!LicenseUtil.checkLicenseStartAndEndTime(latestValidLicenseInfo)) {
            return false;
        }
        final List<String> allIp = NetUtils.getAllIp();
        for (String ip : allIp) {
            try {
                if (LicenseUtil.checkLicenseServer(latestValidLicenseInfo, new HashSet<>(), ip)) {
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    private EngineConfig getEngineConfig() {
        final EngineConfig engineConfig = new EngineConfig();
        return engineConfig;
    }
}
