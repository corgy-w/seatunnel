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

package org.apache.seatunnel.connectors.seatunnel.hive.utils;

import org.apache.seatunnel.shade.com.google.common.collect.ImmutableList;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.seatunnel.file.hadoop.HadoopLoginFactory;
import org.apache.seatunnel.connectors.seatunnel.file.hdfs.config.HdfsConfigOptions;
import org.apache.seatunnel.connectors.seatunnel.hive.config.HiveConfig;
import org.apache.seatunnel.connectors.seatunnel.hive.exception.HiveConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.hive.exception.HiveConnectorException;
import org.apache.seatunnel.connectors.seatunnel.hive.source.config.HiveSourceOptions;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.AlreadyExistsException;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.thrift.TException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import scala.Serializable;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
public abstract class AbstractHiveMetaStoreProxy implements Closeable, Serializable {

    private static final long serialVersionUID = 1L;

    private static final List<String> HADOOP_CONF_FILES =
            ImmutableList.of("hive-site.xml", "hivemetastore-site.xml", "core-site.xml");

    private transient HiveMetaStoreClient hiveMetaStoreClient;

    private final String metastoreUri;
    private final String hiveHadoopConfigPath;
    private final String hiveSitePath;
    private final String krb5Path;
    private final String kerberosPrincipal;
    private final String kerberosKeytabPath;
    private final String remoteUser;

    public AbstractHiveMetaStoreProxy(ReadonlyConfig config) {
        this.metastoreUri = config.get(HiveSourceOptions.METASTORE_URI);
        this.hiveHadoopConfigPath = config.get(HiveConfig.HADOOP_CONF_PATH);
        this.hiveSitePath = config.getOptional(HiveConfig.HIVE_SITE_PATH).orElse(null);
        this.krb5Path = config.getOptional(HdfsConfigOptions.KRB5_PATH).orElse(null);
        this.kerberosPrincipal =
                config.getOptional(HdfsConfigOptions.KERBEROS_PRINCIPAL).orElse(null);
        this.kerberosKeytabPath =
                config.getOptional(HdfsConfigOptions.KERBEROS_KEYTAB_PATH).orElse(null);
        this.remoteUser = config.getOptional(HdfsConfigOptions.REMOTE_USER).orElse(null);
    }

    private synchronized HiveMetaStoreClient getClient() {
        if (hiveMetaStoreClient != null) {
            return hiveMetaStoreClient;
        }

        try {
            HiveConf hiveConf = new HiveConf();
            hiveConf.set("hive.metastore.uris", metastoreUri);

            if (StringUtils.isNotBlank(hiveHadoopConfigPath)) {
                for (String confFile : HADOOP_CONF_FILES) {
                    java.nio.file.Path path = Paths.get(hiveHadoopConfigPath, confFile);
                    if (Files.exists(path)) {
                        hiveConf.addResource(path.toUri().toURL());
                    }
                }
            }

            if (StringUtils.isNotBlank(hiveSitePath)) {
                hiveConf.addResource(new File(hiveSitePath).toURI().toURL());
            }

            if (enableKerberos()) {
                Configuration configuration = new Configuration();
                configuration.set("hadoop.security.authentication", "kerberos");
                this.hiveMetaStoreClient =
                        HadoopLoginFactory.loginWithKerberos(
                                configuration,
                                krb5Path,
                                kerberosPrincipal,
                                kerberosKeytabPath,
                                (conf, ugi) -> createHiveMetaStoreClient(hiveConf));
                log.info("HiveMetaStoreClient created using Kerberos.");
            } else if (enableRemoteUser()) {
                this.hiveMetaStoreClient =
                        HadoopLoginFactory.loginWithRemoteUser(
                                new Configuration(),
                                remoteUser,
                                (conf, ugi) -> createHiveMetaStoreClient(hiveConf));
                log.info("HiveMetaStoreClient created using remote user: {}", remoteUser);
            } else {
                this.hiveMetaStoreClient = createHiveMetaStoreClient(hiveConf);
                log.info("HiveMetaStoreClient created without Kerberos or remote user.");
            }
        } catch (Exception e) {
            throw new HiveConnectorException(
                    HiveConnectorErrorCode.INITIALIZE_HIVE_METASTORE_CLIENT_FAILED,
                    "Failed to initialize HiveMetaStoreClient",
                    e);
        }

        return hiveMetaStoreClient;
    }

    public Table getTable(@NonNull String dbName, @NonNull String tableName) {
        try {
            return getClient().getTable(dbName, tableName);
        } catch (TException e) {
            throw new HiveConnectorException(
                    HiveConnectorErrorCode.GET_HIVE_TABLE_INFORMATION_FAILED,
                    String.format("Get table [%s.%s] information failed", dbName, tableName),
                    e);
        }
    }

    public void addPartitions(
            @NonNull String dbName, @NonNull String tableName, List<String> partitions)
            throws TException {
        for (String partition : partitions) {
            try {
                getClient().appendPartition(dbName, tableName, partition);
            } catch (AlreadyExistsException e) {
                log.warn("Partition {} already exists", partition);
            }
        }
    }

    public void dropPartitions(
            @NonNull String dbName, @NonNull String tableName, List<String> partitions)
            throws TException {
        for (String partition : partitions) {
            getClient().dropPartition(dbName, tableName, partition, false);
        }
    }

    @Override
    public synchronized void close() {
        if (hiveMetaStoreClient != null) {
            hiveMetaStoreClient.close();
            hiveMetaStoreClient = null;
        }
    }

    private boolean enableKerberos() {
        return StringUtils.isNotBlank(kerberosPrincipal)
                && StringUtils.isNotBlank(kerberosKeytabPath);
    }

    private boolean enableRemoteUser() {
        return StringUtils.isNotBlank(remoteUser);
    }

    protected abstract HiveMetaStoreClient createHiveMetaStoreClient(HiveConf hiveConf);
}
