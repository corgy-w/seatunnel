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
