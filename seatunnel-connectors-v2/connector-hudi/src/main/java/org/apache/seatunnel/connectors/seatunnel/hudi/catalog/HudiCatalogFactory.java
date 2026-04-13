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

package org.apache.seatunnel.connectors.seatunnel.hudi.catalog;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.table.catalog.Catalog;
import org.apache.seatunnel.api.table.factory.CatalogFactory;
import org.apache.seatunnel.api.table.factory.Factory;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import com.google.auto.service.AutoService;

import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.CONF_FILES_PATH;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.DATABASE;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.KERBEROS_PRINCIPAL;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.KERBEROS_PRINCIPAL_FILE;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.KRB5_PATH;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.TABLE_DFS_PATH;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.TABLE_NAME;
import static org.apache.seatunnel.connectors.seatunnel.hudi.config.HudiOptions.USE_KERBEROS;
import static org.apache.seatunnel.connectors.seatunnel.hudi.util.HudiUtil.getConfiguration;
import static org.apache.seatunnel.connectors.seatunnel.hudi.util.HudiUtil.initKerberosAuthentication;

@AutoService(Factory.class)
public class HudiCatalogFactory implements CatalogFactory {

    @Override
    public Catalog createCatalog(String catalogName, ReadonlyConfig options) {
        Configuration hadoopConf =
                options.getOptional(CONF_FILES_PATH)
                        .map(confPath -> getConfiguration(confPath))
                        .orElseGet(Configuration::new);
        if (options.get(USE_KERBEROS)) {
            initKerberosAuthentication(
                    hadoopConf,
                    options.getOptional(KRB5_PATH).orElse(null),
                    options.get(KERBEROS_PRINCIPAL),
                    options.get(KERBEROS_PRINCIPAL_FILE));
        }
        return new HudiCatalog(
                catalogName,
                hadoopConf,
                resolveCatalogPath(
                        options.get(TABLE_DFS_PATH),
                        options.getOptional(DATABASE).orElse(null),
                        options.getOptional(TABLE_NAME).orElse(null)));
    }

    @Override
    public String factoryIdentifier() {
        return "Hudi";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .required(TABLE_DFS_PATH)
                .optional(DATABASE, TABLE_NAME, CONF_FILES_PATH, USE_KERBEROS)
                .conditional(
                        USE_KERBEROS, true, KRB5_PATH, KERBEROS_PRINCIPAL, KERBEROS_PRINCIPAL_FILE)
                .build();
    }

    private String resolveCatalogPath(String tableDfsPath, String database, String tableName) {
        Path tablePath = new Path(tableDfsPath);
        if (StringUtils.isNotBlank(tableName)
                && StringUtils.equals(tablePath.getName(), tableName)) {
            Path parent = tablePath.getParent();
            if (parent == null) {
                return tableDfsPath;
            }
            if (StringUtils.isBlank(database)) {
                return parent.toString();
            }
            if (StringUtils.equals(parent.getName(), database)) {
                Path root = parent.getParent();
                return root == null ? parent.toString() : root.toString();
            }
        }
        return tableDfsPath;
    }
}
