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

import org.apache.seatunnel.common.utils.JdbcUrlUtil;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Gbase8aVcAwareConnectionHelper {

    private static final Pattern VC_NAME_PATTERN = Pattern.compile("(?i)(?:^|[?&])vcName=([^&]+)");

    private static final List<String> BOOTSTRAP_DATABASES =
            Arrays.asList("information_schema", "gbase");

    private Gbase8aVcAwareConnectionHelper() {}

    public static Optional<String> extractVcName(String url) {
        if (StringUtils.isBlank(url)) {
            return Optional.empty();
        }
        Matcher matcher = VC_NAME_PATTERN.matcher(url);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    public static Optional<String> extractTargetDatabase(String url) {
        if (StringUtils.isBlank(url)) {
            return Optional.empty();
        }
        return JdbcUrlUtil.getUrlInfo(url).getDefaultDatabase();
    }

    public static boolean isBootstrapDatabase(String databaseName) {
        if (StringUtils.isBlank(databaseName)) {
            return false;
        }
        for (String bootstrapDatabase : BOOTSTRAP_DATABASES) {
            if (bootstrapDatabase.equalsIgnoreCase(databaseName)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> buildBootstrapUrls(String url) {
        JdbcUrlUtil.UrlInfo urlInfo = JdbcUrlUtil.getUrlInfo(url);
        Optional<String> targetDatabase = urlInfo.getDefaultDatabase();
        if (!targetDatabase.isPresent()) {
            List<String> bootstrapUrls = new ArrayList<>();
            for (String bootstrapDatabase : BOOTSTRAP_DATABASES) {
                bootstrapUrls.add(urlInfo.getUrlWithDatabase(bootstrapDatabase));
            }
            bootstrapUrls.add(url);
            return bootstrapUrls;
        }
        if (isBootstrapDatabase(targetDatabase.get())) {
            return Collections.singletonList(url);
        }
        List<String> bootstrapUrls = new ArrayList<>();
        for (String bootstrapDatabase : BOOTSTRAP_DATABASES) {
            bootstrapUrls.add(urlInfo.getUrlWithDatabase(bootstrapDatabase));
        }
        return bootstrapUrls;
    }

    public static void initializeSession(
            Connection connection, String vcName, String targetDatabase) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("USE VC " + vcName);
            if (StringUtils.isNotBlank(targetDatabase) && !isBootstrapDatabase(targetDatabase)) {
                statement.execute("USE " + targetDatabase);
            }
        }
    }
}
