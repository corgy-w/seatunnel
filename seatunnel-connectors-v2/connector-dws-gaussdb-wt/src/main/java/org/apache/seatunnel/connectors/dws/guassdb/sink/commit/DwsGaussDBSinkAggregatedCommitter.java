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

package org.apache.seatunnel.connectors.dws.guassdb.sink.commit;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.sink.SinkAggregatedCommitter;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.connectors.dws.guassdb.catalog.DwsGaussDBCatalog;
import org.apache.seatunnel.connectors.dws.guassdb.catalog.DwsGaussDBCatalogFactory;
import org.apache.seatunnel.connectors.dws.guassdb.sink.sql.DwsGaussSqlGenerator;

import org.apache.commons.collections4.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DwsGaussDBSinkAggregatedCommitter
        implements SinkAggregatedCommitter<
                DwsGaussDBSinkCommitInfo, DwsGaussDBSinkAggregatedCommitInfo> {

    private final DwsGaussSqlGenerator dwsGaussSqlGenerator;
    private final DwsGaussDBCatalog dwsGaussDBCatalog;

    public DwsGaussDBSinkAggregatedCommitter(
            DwsGaussSqlGenerator dwsGaussSqlGenerator,
            CatalogTable catalogTable,
            ReadonlyConfig readonlyConfig) {
        this.dwsGaussSqlGenerator = dwsGaussSqlGenerator;
        this.dwsGaussDBCatalog =
                new DwsGaussDBCatalogFactory()
                        .createCatalog(catalogTable.getCatalogName(), readonlyConfig);
    }

    @Override
    public List<DwsGaussDBSinkAggregatedCommitInfo> commit(
            List<DwsGaussDBSinkAggregatedCommitInfo> aggregatedCommitInfo) throws IOException {
        if (CollectionUtils.isEmpty(aggregatedCommitInfo)) {
            return Collections.emptyList();
        }

        List<DwsGaussDBSinkAggregatedCommitInfo> needRetryCommitInfo = new ArrayList<>();
        for (DwsGaussDBSinkAggregatedCommitInfo commitInfo : aggregatedCommitInfo) {
            try {
                for (Long snapshotId : commitInfo.getCurrentSnapshotIds()) {
                    dealWithDeleteRowsInTemporaryTable(snapshotId);
                    // using thread-pool
                    mergeTemporaryIntoTargetTable(snapshotId);
                }
                clearTemporaryTable(commitInfo.getCurrentSnapshotIds());
            } catch (Exception ex) {
                log.error(
                        "DwsGaussDBSinkAggregatedCommitter commit failed, commitInfo: {}",
                        commitInfo,
                        ex);
                needRetryCommitInfo.add(commitInfo);
            }
        }
        return needRetryCommitInfo;
    }

    @Override
    public DwsGaussDBSinkAggregatedCommitInfo combine(List<DwsGaussDBSinkCommitInfo> commitInfos) {
        if (CollectionUtils.isEmpty(commitInfos)) {
            return null;
        }
        DwsGaussDBSinkCommitInfo dwsGaussDBSinkCommitInfo = commitInfos.get(0);
        List<Long> snapshotIds =
                commitInfos.stream()
                        .map(DwsGaussDBSinkCommitInfo::getCurrentSnapshotId)
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
        return new DwsGaussDBSinkAggregatedCommitInfo(
                dwsGaussDBSinkCommitInfo.getTargetTableName(),
                dwsGaussDBSinkCommitInfo.getTemporaryTableName(),
                snapshotIds,
                dwsGaussDBSinkCommitInfo.getRowType());
    }

    @Override
    public void abort(List<DwsGaussDBSinkAggregatedCommitInfo> aggregatedCommitInfo) {
        // clear the temporary table
        if (CollectionUtils.isEmpty(aggregatedCommitInfo)) {
            return;
        }
        for (DwsGaussDBSinkAggregatedCommitInfo dwsGaussDBSinkAggregatedCommitInfo :
                aggregatedCommitInfo) {
            // todo: use batch clear
            clearTemporaryTable(dwsGaussDBSinkAggregatedCommitInfo.getCurrentSnapshotIds());
        }
    }

    @Override
    public void close() {
        try (DwsGaussDBCatalog dwsGaussDBCatalog1 = dwsGaussDBCatalog) {}
    }

    private void dealWithDeleteRowsInTemporaryTable(Long currentSnapshotId) {
        String deleteRowsInTargetTableSql =
                dwsGaussSqlGenerator.getDeleteRowsInTargetTableSql(currentSnapshotId);
        dwsGaussDBCatalog.executeUpdateSql(deleteRowsInTargetTableSql);

        String deleteRowsInTemporaryTableSql =
                dwsGaussSqlGenerator.getDeleteRowsInTemporaryTableSql(currentSnapshotId);
        dwsGaussDBCatalog.executeUpdateSql(deleteRowsInTemporaryTableSql);
    }

    private void mergeTemporaryIntoTargetTable(Long currentSnapshotId) {
        String mergeInTargetTableSql =
                dwsGaussSqlGenerator.getMergeInTargetTableSql(currentSnapshotId);
        dwsGaussDBCatalog.executeUpdateSql(mergeInTargetTableSql);
    }

    private void clearTemporaryTable(List<Long> currentSnapshotIds) {
        if (CollectionUtils.isEmpty(currentSnapshotIds)) {
            return;
        }
        String deleteTemporarySnapshotSql =
                dwsGaussSqlGenerator.getDeleteTemporarySnapshotSql(currentSnapshotIds);
        dwsGaussDBCatalog.executeUpdateSql(deleteTemporarySnapshotSql);
    }
}
