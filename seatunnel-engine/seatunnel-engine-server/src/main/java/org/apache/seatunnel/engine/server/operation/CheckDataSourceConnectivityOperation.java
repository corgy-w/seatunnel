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

package org.apache.seatunnel.engine.server.operation;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.Catalog;
import org.apache.seatunnel.api.table.factory.FactoryUtil;
import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.engine.common.utils.concurrent.CompletableFuture;
import org.apache.seatunnel.engine.core.job.DataSourceConnectivityCheckResult;
import org.apache.seatunnel.engine.server.serializable.ClientToServerOperationDataSerializerHook;

import com.hazelcast.internal.serialization.Data;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.impl.AllowedDuringPassiveState;
import com.hazelcast.spi.impl.operationservice.Operation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CheckDataSourceConnectivityOperation extends Operation
        implements IdentifiedDataSerializable, AllowedDuringPassiveState {

    private String catalogIdentifier;
    private String optionsJson;
    private int timeoutMs;

    private Data response;

    public CheckDataSourceConnectivityOperation() {}

    public CheckDataSourceConnectivityOperation(
            String catalogIdentifier, String optionsJson, int timeoutMs) {
        this.catalogIdentifier = catalogIdentifier;
        this.optionsJson = optionsJson;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public int getFactoryId() {
        return ClientToServerOperationDataSerializerHook.FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return ClientToServerOperationDataSerializerHook.CHECK_DATASOURCE_CONNECTIVITY_OPERATION;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeString(catalogIdentifier);
        out.writeString(optionsJson);
        out.writeInt(timeoutMs);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        this.catalogIdentifier = in.readString();
        this.optionsJson = in.readString();
        this.timeoutMs = in.readInt();
    }

    @Override
    public void run() {
        final long startNanos = System.nanoTime();
        boolean success = false;
        String error = null;
        CompletableFuture<Void> future =
                CompletableFuture.supplyAsync(
                        () -> {
                            doCheckConnectivity(catalogIdentifier);
                            return null;
                        },
                        getNodeEngine()
                                .getExecutionService()
                                .getExecutor("check_datasource_connectivity_operation"));
        try {
            if (timeoutMs > 0) {
                future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                future.get();
            }
            success = true;
        } catch (TimeoutException e) {
            future.cancel(true);
            error = "Timed out after " + timeoutMs + " ms";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            error = "Interrupted while checking datasource connectivity";
        } catch (ExecutionException e) {
            error = extractErrorMessage(e.getCause());
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            DataSourceConnectivityCheckResult result = new DataSourceConnectivityCheckResult();
            result.setCatalogIdentifier(catalogIdentifier);
            result.setExecutionMode(DataSourceConnectivityCheckResult.ExecutionMode.MASTER_ONLY);
            result.setTimeoutMs(timeoutMs);

            DataSourceConnectivityCheckResult.MemberResult memberResult =
                    new DataSourceConnectivityCheckResult.MemberResult();
            memberResult.setAddress(getLocalMemberAddress());
            memberResult.setSuccess(success);
            memberResult.setError(error);
            memberResult.setElapsedMs(elapsedMs);
            result.addMemberResult(memberResult);
            response = this.getNodeEngine().toData(result);
        }
    }

    @Override
    public Object getResponse() {
        return response;
    }

    private String getLocalMemberAddress() {
        if (getNodeEngine() == null || getNodeEngine().getLocalMember() == null) {
            return "unknown";
        }
        return getNodeEngine().getLocalMember().getAddress().getHost()
                + ":"
                + getNodeEngine().getLocalMember().getAddress().getPort();
    }

    private void doCheckConnectivity(String catalogIdentifier) {
        Map<String, Object> optionsMap = new LinkedHashMap<>();
        if (optionsJson != null && !optionsJson.trim().isEmpty()) {
            optionsMap.putAll(JsonUtils.toMap(optionsJson));
        }
        ReadonlyConfig options = ReadonlyConfig.fromMap(optionsMap);
        Optional<Catalog> catalogOptional =
                FactoryUtil.createOptionalCatalog(
                        catalogIdentifier,
                        options,
                        Thread.currentThread().getContextClassLoader(),
                        catalogIdentifier);
        if (!catalogOptional.isPresent()) {
            throw new IllegalStateException("Catalog factory not found");
        }
        Catalog catalog = catalogOptional.get();
        catalog.open();
        try {
            // open() is the current connectivity contract for this operation.
        } finally {
            catalog.close();
        }
    }

    private String extractErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current == null ? null : current.getMessage();
    }
}
