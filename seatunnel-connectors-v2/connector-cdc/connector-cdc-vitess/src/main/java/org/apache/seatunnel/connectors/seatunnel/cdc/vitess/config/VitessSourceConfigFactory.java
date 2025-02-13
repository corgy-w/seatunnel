/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.seatunnel.connectors.seatunnel.cdc.vitess.config;

import org.apache.seatunnel.connectors.cdc.base.config.JdbcSourceConfigFactory;

import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

public class VitessSourceConfigFactory extends JdbcSourceConfigFactory {

    private static final String DATABASE_SERVER_NAME = "vitess_transaction_log_source";
    private static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

    private String tabletType;

    @Override
    public VitessSourceConfig create(int subtask) {
        Properties props = new Properties();
        props.setProperty("database.server.name", DATABASE_SERVER_NAME);
        props.setProperty("database.hostname", checkNotNull(hostname));
        props.setProperty("database.user", checkNotNull(username));
        props.setProperty("database.password", checkNotNull(password));
        props.setProperty("database.port", String.valueOf(port));

        if (databaseList != null) {
            props.setProperty("database.include.list", String.join(",", databaseList));
        }
        if (tableList != null) {
            props.setProperty("table.include.list", String.join(",", tableList));
        }
        if (serverTimeZone != null) {
            props.setProperty("database.serverTimezone", serverTimeZone);
        }
        if (tabletType != null) {
            props.setProperty("vitess.tablet.type", tabletType);
        }

        // override the user-defined debezium properties
        if (dbzProperties != null) {
            props.putAll(dbzProperties);
        }

        return new VitessSourceConfig(
                startupConfig,
                stopConfig,
                databaseList,
                tableList,
                splitSize,
                distributionFactorUpper,
                distributionFactorLower,
                sampleShardingThreshold,
                inverseSamplingRate,
                props,
                DRIVER_CLASS_NAME,
                hostname,
                port,
                username,
                password,
                originUrl,
                fetchSize,
                serverTimeZone,
                connectTimeoutMillis,
                connectMaxRetries,
                connectionPoolSize,
                exactlyOnce);
    }

    public void useTabletType(String tabletType) {
        this.tabletType = tabletType;
    }
}
