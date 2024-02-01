/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.dws.guassdb.catalog;

import org.apache.seatunnel.shade.com.google.common.base.Preconditions;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.configuration.util.OptionValidationException;
import org.apache.seatunnel.api.table.factory.CatalogFactory;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;
import org.apache.seatunnel.connectors.dws.guassdb.config.DwsGaussDBConfig;
import org.apache.seatunnel.connectors.dws.guassdb.sink.config.DwsGaussDBSinkOption;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class DwsGaussDBCatalogFactory implements CatalogFactory {

    @Override
    public DwsGaussDBCatalog createCatalog(String catalogName, ReadonlyConfig options) {
        String urlWithDatabase = options.get(DwsGaussDBSinkOption.URL);
        Preconditions.checkArgument(
                StringUtils.isNotEmpty(urlWithDatabase),
                "Miss config url! Please check your config.");
        JdbcUrlUtil.UrlInfo urlInfo = JdbcUrlUtil.getUrlInfo(urlWithDatabase);
        Optional<String> defaultDatabase = urlInfo.getDefaultDatabase();
        if (!defaultDatabase.isPresent()) {
            throw new OptionValidationException(DwsGaussDBSinkOption.URL);
        }
        return new DwsGaussDBCatalog(
                catalogName,
                options.get(DwsGaussDBSinkOption.USER),
                options.get(DwsGaussDBSinkOption.PASSWORD),
                urlInfo,
                options.get(DwsGaussDBSinkOption.PROPERTIES),
                options.get(DwsGaussDBSinkOption.DATABASE_SCHEMA));
    }

    @Override
    public String factoryIdentifier() {
        return DwsGaussDBConfig.CONNECTOR_NAME;
    }

    @Override
    public OptionRule optionRule() {
        return new DwsGaussDBCatalogOption().getOptionRule();
    }
}
