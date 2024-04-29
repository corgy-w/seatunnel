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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.whaleops.license.dto.LicensePackageDto;
import org.whaleops.license.dto.SystemLicenseInfo;
import org.whaleops.license.enums.LicenseVersionEnum;
import org.whaleops.license.service.LicenseService;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WhaleTunnelLicenseServiceImpl implements LicenseService {

    private static final Logger log = LoggerFactory.getLogger(WhaleTunnelLicenseServiceImpl.class);

    private EngineConfig engineConfig;

    private static final String LICENSE_PATH =
            System.getProperty("SEATUNNEL_LICENCE_HOME") == null
                    ? "/etc/seatunnel/whaletunnel.license"
                    : System.getProperty("SEATUNNEL_LICENCE_HOME");

    public WhaleTunnelLicenseServiceImpl(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @SneakyThrows
    @Override
    public List<SystemLicenseInfo> loadSystemLicenseList() {
        final SystemLicenseInfo systemLicenseInfo = new SystemLicenseInfo();
        final String licenseStringFromApi = getLicenseStringFromApi();
        if (StringUtils.isNotEmpty(licenseStringFromApi)) {
            systemLicenseInfo.setLicense(licenseStringFromApi);
        } else {
            InputStream inputStream = new FileInputStream(LICENSE_PATH);
            StringBuilder stringBuilder = new StringBuilder();
            if (inputStream != null) {
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                } catch (IOException e) {
                    throw new SeaTunnelException(e);
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new SeaTunnelException(e);
                    }
                }
            } else {
                throw new SeaTunnelException("inputStream is null");
            }
            systemLicenseInfo.setLicense(stringBuilder.toString());
        }
        systemLicenseInfo.setStatus(1);
        List<SystemLicenseInfo> systemLicenseInfoList = new ArrayList<>(1);
        systemLicenseInfoList.add(systemLicenseInfo);
        return systemLicenseInfoList;
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
                log.info("get license fail:{}", response.toString());
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

    /*public static void main(String[] args) {
        final EngineConfig engineConfig = new EngineConfig();
        Map<String,String> header = new HashMap<>();
        header.put("Content-Type","application/json");
        header.put("Token","e554d577183d034b90dd47d457d58b61");
        engineConfig.setLicenseGetHttpApi("http://localhost:12345/dolphinscheduler/license/get-valid-license");
        engineConfig.setLicenseGetHttpHeaders(header);
        final WhaleTunnelLicenseServiceImpl whaleTunnelLicenseService = new WhaleTunnelLicenseServiceImpl(engineConfig);
        whaleTunnelLicenseService.getLicenseStringFromApi();
    }*/

    @Override
    public SystemLicenseInfo getValidLicense() {
        return null;
    }

    @Override
    public List<String> getFeatureList(LicenseVersionEnum licenseVersionEnum) {
        return null;
    }

    @Override
    public List<String> getExcludeFeatureList(LicenseVersionEnum licenseVersionEnum) {
        return null;
    }

    @Override
    public List<LicensePackageDto> queryBaseLicenseImportIncrementalPackages(Integer integer) {
        return new ArrayList<>();
    }

    @Override
    public Set<String> getAllServerIpListWithCache() {
        return null;
    }

    @Override
    public String getDBUrl() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }
}
