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

package org.apache.seatunnel.core.starter.seatunnel.command;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.ConfigValidator;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.connector.TableSource;
import org.apache.seatunnel.api.table.factory.FactoryUtil;
import org.apache.seatunnel.api.table.factory.TableSourceFactory;
import org.apache.seatunnel.api.table.factory.TableSourceFactoryContext;
import org.apache.seatunnel.common.config.Common;
import org.apache.seatunnel.common.exception.SeaTunnelRuntimeException;
import org.apache.seatunnel.common.utils.ExceptionUtils;
import org.apache.seatunnel.common.utils.FileUtils;
import org.apache.seatunnel.core.starter.command.Command;
import org.apache.seatunnel.core.starter.exception.CommandExecuteException;
import org.apache.seatunnel.core.starter.seatunnel.args.ClientCommandArgs;
import org.apache.seatunnel.core.starter.seatunnel.args.ModelPushCommandArgs;
import org.apache.seatunnel.core.starter.seatunnel.modelpush.Result;
import org.apache.seatunnel.engine.common.loader.SeaTunnelChildFirstClassLoader;
import org.apache.seatunnel.plugin.discovery.PluginIdentifier;
import org.apache.seatunnel.plugin.discovery.seatunnel.SeaTunnelSinkPluginDiscovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** This command is used to execute the SeaTunnel engine job by SeaTunnel API. */
@Slf4j
public class ModelPushCommand implements Command<ClientCommandArgs> {

    private final ModelPushCommandArgs args;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ModelPushCommand(ModelPushCommandArgs modelPushCommandArgs) {
        this.args = modelPushCommandArgs;
    }

    @Override
    public void execute() throws CommandExecuteException {
        SeaTunnelSinkPluginDiscovery discovery = new SeaTunnelSinkPluginDiscovery();
        List<URL> paths =
                discovery.getPluginJarAndDependencyPaths(
                        Collections.singletonList(
                                PluginIdentifier.of(
                                        "seatunnel", args.getType(), args.getConnector())));
        Path libPath = Common.libDir();
        if (libPath.toFile().exists()) {
            try {
                paths.addAll(FileUtils.searchJarFiles(libPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ClassLoader classLoader = new SeaTunnelChildFirstClassLoader(paths);
        Thread.currentThread().setContextClassLoader(classLoader);

        Result result = new Result();
        try {
            List<CatalogTable> tables =
                    getSeaTunnelSource(
                                    ReadonlyConfig.fromMap(
                                            OBJECT_MAPPER.readValue(args.getConfig(), Map.class)),
                                    args.getConnector())
                            .getProducedCatalogTables();
            for (CatalogTable table : tables) {
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("tableId", OBJECT_MAPPER.convertValue(table.getTableId(), Map.class));
                tableInfo.put(
                        "tableSchema",
                        OBJECT_MAPPER.convertValue(table.getTableSchema(), Map.class));
                tableInfo.put("options", table.getOptions());
                tableInfo.put("partitionKeys", table.getPartitionKeys());
                tableInfo.put("comment", table.getComment());
                tableInfo.put("catalogName", table.getCatalogName());
                result.getCatalogTables().add(tableInfo);
            }
        } catch (SeaTunnelRuntimeException e) {
            result.setExceptionInfo(
                    new Result.ExceptionInfo(
                            e.getClass().getName(),
                            e.getMessage(),
                            ExceptionUtils.getMessage(e),
                            e.getSeaTunnelErrorCode().getCode(),
                            e.getParams()));
        } catch (Throwable e) {
            result.setExceptionInfo(
                    new Result.ExceptionInfo(
                            e.getClass().getName(),
                            e.getMessage(),
                            ExceptionUtils.getMessage(e),
                            null,
                            null));
        }
        try {
            System.out.println(OBJECT_MAPPER.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public SeaTunnelSource getSeaTunnelSource(ReadonlyConfig options, String factoryIdentifier) {
        TableSourceFactory tableSourceFactory =
                FactoryUtil.discoverFactory(
                        Thread.currentThread().getContextClassLoader(),
                        TableSourceFactory.class,
                        factoryIdentifier);
        // catalog config
        return createAndPrepareSource(
                tableSourceFactory, options, Thread.currentThread().getContextClassLoader());
    }

    private <T, SplitT extends SourceSplit, StateT extends Serializable>
            SeaTunnelSource<T, SplitT, StateT> createAndPrepareSource(
                    TableSourceFactory factory, ReadonlyConfig options, ClassLoader classLoader) {
        TableSourceFactoryContext context = new TableSourceFactoryContext(options, classLoader);
        ConfigValidator.of(context.getOptions()).validate(factory.optionRule());
        TableSource<T, SplitT, StateT> tableSource = factory.createSource(context);
        return tableSource.createSource();
    }
}
