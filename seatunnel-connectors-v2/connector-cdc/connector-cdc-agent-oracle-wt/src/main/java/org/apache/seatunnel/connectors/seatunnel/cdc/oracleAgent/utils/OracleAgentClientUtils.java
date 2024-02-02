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

package org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.utils;

import org.apache.seatunnel.common.utils.RetryUtils;

import org.apache.commons.collections4.CollectionUtils;

import org.whaleops.whaletunnel.oracleagent.sdk.OracleAgentClient;
import org.whaleops.whaletunnel.oracleagent.sdk.OracleAgentException;
import org.whaleops.whaletunnel.oracleagent.sdk.model.OracleOperation;
import org.whaleops.whaletunnel.oracleagent.sdk.model.OracleTransactionData;
import org.whaleops.whaletunnel.oracleagent.sdk.model.OracleTransactionDataFetchRequest;
import org.whaleops.whaletunnel.oracleagent.sdk.model.OracleTransactionFileNumberFetchRequest;

import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

@Slf4j
public class OracleAgentClientUtils {

    public static List<OracleTransactionData> fetchOracleTransactionData(
            OracleAgentClient oracle9BridgeClient,
            List<String> tableOwners,
            List<String> tables,
            Integer fzsFileNumber) {
        return fetchOracleTransactionData(
                oracle9BridgeClient,
                new OracleTransactionDataFetchRequest(tableOwners, tables, fzsFileNumber));
    }

    public static List<OracleTransactionData> fetchOracleTransactionData(
            OracleAgentClient oracle9BridgeClient, OracleTransactionDataFetchRequest request) {
        try {
            return oracle9BridgeClient.fetchTransactionData(request);
        } catch (OracleAgentException e) {
            throw new RuntimeException("Query oracle transaction error, request: " + request, e);
        }
    }

    public static Long currentMaxScn(
            OracleAgentClient oracle9BridgeClient,
            String tableOwner,
            String table,
            Integer fzsFileNumber) {
        return currentMaxScn(
                oracle9BridgeClient,
                Collections.singletonList(tableOwner),
                Collections.singletonList(table),
                fzsFileNumber);
    }

    public static Long currentMaxScn(
            OracleAgentClient oracle9BridgeClient,
            List<String> tableOwners,
            List<String> tables,
            Integer fzsFileNumber) {
        try {
            List<OracleTransactionData> oracleTransactionData =
                    fetchOracleTransactionData(
                            oracle9BridgeClient,
                            new OracleTransactionDataFetchRequest(
                                    tableOwners, tables, fzsFileNumber));
            if (oracleTransactionData.isEmpty()) {
                return 0L;
            }
            long maxScn = 0L;
            for (OracleTransactionData transactionData : oracleTransactionData) {
                for (OracleOperation op : transactionData.getOp()) {
                    String scn = op.getScn();
                    BigInteger bigInteger = new BigInteger(scn, 16);
                    maxScn = Math.max(maxScn, bigInteger.longValue());
                }
            }
            return maxScn;
        } catch (Throwable throwable) {
            throw new RuntimeException(
                    "Query currentMaxScn error, request: "
                            + tableOwners
                            + " "
                            + tables
                            + " "
                            + fzsFileNumber,
                    throwable);
        }
    }

    public static Long currentMinScn(
            OracleAgentClient oracle9BridgeClient,
            String tableOwner,
            String table,
            Integer fzsFileNumber) {
        return currentMinScn(
                oracle9BridgeClient,
                Collections.singletonList(tableOwner),
                Collections.singletonList(table),
                fzsFileNumber);
    }

    public static Long currentMinScn(
            OracleAgentClient oracle9BridgeClient,
            List<String> tableOwners,
            List<String> tables,
            Integer fzsFileNumber) {
        List<OracleTransactionData> oracleTransactionData =
                fetchOracleTransactionData(
                        oracle9BridgeClient,
                        new OracleTransactionDataFetchRequest(tableOwners, tables, fzsFileNumber));
        if (oracleTransactionData.isEmpty()) {
            return null;
        }
        long minScn = 0L;
        for (OracleTransactionData transactionData : oracleTransactionData) {
            // todo: directly return the first scn?
            for (OracleOperation op : transactionData.getOp()) {
                String scn = op.getScn();
                BigInteger bigInteger = new BigInteger(scn, 16);
                minScn = Math.min(minScn, bigInteger.longValue());
            }
        }
        return minScn;
    }

    public static Integer currentMinFzsFileNumber(
            OracleAgentClient oracle9BridgeClient, String tableOwner, String table) {
        return currentMaxFzsFileNumber(
                oracle9BridgeClient,
                new OracleTransactionFileNumberFetchRequest(tableOwner, table));
    }

    public static Integer currentMinFzsFileNumber(
            OracleAgentClient oracle9BridgeClient, List<String> tableOwner, List<String> table) {
        return currentMinFzsFileNumber(
                oracle9BridgeClient,
                new OracleTransactionFileNumberFetchRequest(tableOwner, table));
    }

    public static Integer currentMinFzsFileNumber(
            OracleAgentClient oracle9BridgeClient,
            OracleTransactionFileNumberFetchRequest fetchRequest) {
        // todo: can we directly get the min fzs number?
        Integer maxTransactionFileNumber =
                currentMaxFzsFileNumber(oracle9BridgeClient, fetchRequest);
        if (maxTransactionFileNumber == null) {
            throw new RuntimeException(
                    "Query min file number error, request: "
                            + fetchRequest
                            + " , Please check if the fzs file is missing");
        }
        int minFzsFileNumber = 0;
        while (minFzsFileNumber <= maxTransactionFileNumber) {
            List<OracleTransactionData> oracleTransactionData =
                    fetchOracleTransactionData(
                            oracle9BridgeClient,
                            new OracleTransactionDataFetchRequest(
                                    fetchRequest.getTableOwner(),
                                    fetchRequest.getTable(),
                                    minFzsFileNumber));
            if (CollectionUtils.isNotEmpty(oracleTransactionData)) {
                return minFzsFileNumber;
            }
            minFzsFileNumber++;
        }
        //        while (minFzsFileNumber < maxTransactionFileNumber) {
        //            int mid = (minFzsFileNumber + maxTransactionFileNumber) / 2;
        //            List<OracleTransactionData> oracleTransactionData =
        //                    fetchOracleTransactionData(
        //                            oracle9BridgeClient,
        //                            new OracleTransactionDataFetchRequest(
        //                                    fetchRequest.getTableOwner(), fetchRequest.getTable(),
        // mid));
        //            if (CollectionUtils.isNotEmpty(oracleTransactionData)) {
        //                maxTransactionFileNumber = mid;
        //            } else {
        //                minFzsFileNumber = mid + 1;
        //            }
        //        }
        log.info(
                "Current min fzs file number for table: {} owner: {} is: {}",
                fetchRequest.getTable(),
                fetchRequest.getTableOwner(),
                minFzsFileNumber);
        return minFzsFileNumber;
    }

    public static Integer currentMaxFzsFileNumber(
            OracleAgentClient oracle9BridgeClient, String tableOwner, String table) {
        return currentMaxFzsFileNumber(
                oracle9BridgeClient,
                new OracleTransactionFileNumberFetchRequest(tableOwner, table));
    }

    public static Integer currentMaxFzsFileNumber(
            OracleAgentClient oracle9BridgeClient, List<String> tableOwner, List<String> table) {
        return currentMaxFzsFileNumber(
                oracle9BridgeClient,
                new OracleTransactionFileNumberFetchRequest(tableOwner, table));
    }

    public static Integer currentMaxFzsFileNumber(
            OracleAgentClient oracle9BridgeClient,
            OracleTransactionFileNumberFetchRequest fetchRequest) {
        // get the max fzs and scn
        try {
            Integer maxFzsFileNumber =
                    RetryUtils.retryWithException(
                            () ->
                                    oracle9BridgeClient.getCurrentMaxTransactionFileNumber(
                                            fetchRequest),
                            new RetryUtils.RetryMaterial(3, true, e -> true, 1_000));
            log.debug("Current max fzs file number: {}", maxFzsFileNumber);
            // avoid -1
            maxFzsFileNumber = Math.max(maxFzsFileNumber, 0);
            return maxFzsFileNumber;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Query max file number error, fetchRequest: " + fetchRequest, e);
        }
    }
}
