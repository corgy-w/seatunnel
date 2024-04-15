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

import org.apache.seatunnel.engine.server.service.WhaleTunnelLicenseServiceImpl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.whaleops.license.LicenseManager;
import org.whaleops.license.dto.LicenseInfo;
import org.whaleops.license.utils.LicenseUtil;

import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.HashSet;

public class LicenseTest {

    @Test
    public void testLicense() {
        Assertions.assertFalse(isPassedLicenseCheck());
    }

    @SneakyThrows
    private Boolean isPassedLicenseCheck() {
        LicenseManager licenseManager = new LicenseManager();
        Class<?> clazz = licenseManager.getClass();
        Field nameField = clazz.getDeclaredField("licenseService");
        nameField.setAccessible(true);
        nameField.set(licenseManager, new WhaleTunnelLicenseServiceImpl());

        licenseManager.init();
        final LicenseInfo latestValidLicenseInfo = licenseManager.getLatestValidLicenseInfo();
        if (!LicenseUtil.checkLicenseStartAndEndTime(latestValidLicenseInfo)) {
            return false;
        }
        return LicenseUtil.checkLicenseServer(
                latestValidLicenseInfo,
                new HashSet<>(),
                InetAddress.getLocalHost().getHostAddress());
    }
}
