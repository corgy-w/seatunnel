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

package org.apache.seatunnel.engine.server.license;

import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.common.utils.SeaTunnelException;
import org.apache.seatunnel.engine.common.config.EngineConfig;
import org.apache.seatunnel.engine.server.utils.HttpUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import org.whaleops.license.dto.LicenseInfo;
import org.whaleops.license.dto.SystemId;
import org.whaleops.license.dto.SystemLicenseInfo;
import org.whaleops.license.entity.LicenseParams;
import org.whaleops.license.utils.LicenseDecryptUtil;
import org.whaleops.license.utils.LicenseUtil;
import org.whaleops.license.utils.SystemIdUtil;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

@Slf4j
public class LicenseDelegator {

    private final EngineConfig engineConfig;

    private static final String LICENSE_PATH =
            System.getProperty("SEATUNNEL_LICENCE_HOME") == null
                    ? "/etc/seatunnel/whaletunnel.license"
                    : System.getProperty("SEATUNNEL_LICENCE_HOME");

    public LicenseDelegator(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @SneakyThrows
    public LicenseInfo loadSystemLicense() {
        final SystemLicenseInfo systemLicenseInfo = new SystemLicenseInfo();

        final String licenseStringFromApi = getLicenseStringFromApi();
        if (StringUtils.isNotEmpty(licenseStringFromApi)) {
            systemLicenseInfo.setLicense(licenseStringFromApi);
        } else {
            final String licenseFromFile = getLicenseFromFile();
            systemLicenseInfo.setLicense(licenseFromFile);
        }

        systemLicenseInfo.setStatus(1);

        final LicenseParams licenseParams =
                LicenseDecryptUtil.decrypt2License(systemLicenseInfo.getLicense());
        final SystemId systemId = SystemIdUtil.decrypt2SystemId(licenseParams.getSystemId());
        LicenseUtil.recalculateIpSet(
                systemLicenseInfo.getLicense(), systemId.getWsIpList(), systemId.getWtIpList());
        return new LicenseInfo(systemLicenseInfo, licenseParams, systemId);
    }

    private String getLicenseFromFile() {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = new FileInputStream(LICENSE_PATH);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            throw new SeaTunnelException(e);
        }
        return stringBuilder.toString();
    }

    private String getLicenseStringFromApi() {
        final String licenseGetHttpApi = engineConfig.getLicenseGetHttpApi();
        final Map<String, String> licenseGetHttpHeaders = engineConfig.getLicenseGetHttpHeaders();
        if (StringUtils.isBlank(licenseGetHttpApi) || MapUtils.isEmpty(licenseGetHttpHeaders)) {
            return null;
        }
        OkHttpClient httpClient = HttpUtils.getInstance();
        try {
            Request.Builder requestBuilder = new Request.Builder().url(licenseGetHttpApi).get();
            licenseGetHttpHeaders.forEach(requestBuilder::header);
            Response response = httpClient.newCall(requestBuilder.build()).execute();
            if (!response.isSuccessful()) {
                log.info("get license fail:{}", response);
                return null;
            }
            final String body = response.body().string();
            final ObjectNode jsonNodes = JsonUtils.parseObject(body);
            final JsonNode data = jsonNodes.get("data");
            final JsonNode systemLicense = data.get("systemLicense");
            final JsonNode licenseStrNode = systemLicense.get("license");
            final String license = licenseStrNode.asText();
            return license;
        } catch (Exception e) {
            log.error("get license error:{}", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
