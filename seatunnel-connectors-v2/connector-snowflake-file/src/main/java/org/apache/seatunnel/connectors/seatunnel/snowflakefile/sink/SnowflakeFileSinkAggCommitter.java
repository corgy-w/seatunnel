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

package org.apache.seatunnel.connectors.seatunnel.snowflakefile.sink;

import org.apache.seatunnel.api.sink.SinkAggregatedCommitter;
import org.apache.seatunnel.api.sink.SupportMultiTableSinkAggregatedCommitter;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.config.SnowflakeFileConfig;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.sink.client.SnowflakeCopyIntoClient;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.state.SnowflakeFileAggCommitInfo;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.state.SnowflakeFileCommitInfo;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SnowflakeFileSinkAggCommitter
        implements SinkAggregatedCommitter<SnowflakeFileCommitInfo, SnowflakeFileAggCommitInfo>,
                SupportMultiTableSinkAggregatedCommitter<Void> {

    private final SnowflakeFileConfig config;
    private SnowflakeCopyIntoClient snowflakeClient;

    public SnowflakeFileSinkAggCommitter(SnowflakeFileConfig config) {
        this.config = config;
    }

    @Override
    public List<SnowflakeFileAggCommitInfo> commit(
            List<SnowflakeFileAggCommitInfo> aggregatedCommitInfos) throws IOException {
        if (aggregatedCommitInfos == null || aggregatedCommitInfos.isEmpty()) {
            log.info("No aggregated commit info to commit");
            return new ArrayList<>();
        }

        log.info("Starting commit with {} aggregated commit infos", aggregatedCommitInfos.size());

        try {
            ensureConnected();

            // 合并所有提交信息
            SnowflakeFileAggCommitInfo mergedInfo = mergeCommitInfos(aggregatedCommitInfos);

            if (mergedInfo.getTotalFiles() == 0) {
                log.info("No files to copy into Snowflake");
                return new ArrayList<>();
            }

            // 收集所有 S3 文件
            List<String> allS3Files = collectAllS3Files(mergedInfo);

            log.info(
                    "Committing {} files ({} rows) to Snowflake table {}.{}.{}",
                    mergedInfo.getTotalFiles(),
                    mergedInfo.getTotalRows(),
                    config.getDatabase(),
                    config.getSchema(),
                    config.getTable());

            // 执行 COPY INTO 命令
            snowflakeClient.copyIntoTable(allS3Files);
            cleanupLocalFiles(allS3Files);

            log.info("Successfully committed {} files to Snowflake", mergedInfo.getTotalFiles());

            return new ArrayList<>(); // 成功提交后返回空列表

        } catch (SQLException e) {
            log.error("Failed to commit files to Snowflake", e);
            throw new IOException("Failed to commit files to Snowflake", e);
        }
    }

    @Override
    public SnowflakeFileAggCommitInfo combine(List<SnowflakeFileCommitInfo> commitInfos) {
        if (commitInfos == null || commitInfos.isEmpty()) {
            return new SnowflakeFileAggCommitInfo();
        }

        // 创建合并后的提交信息
        Map<String, List<String>> allPartitionFiles = new HashMap<>();
        long totalRows = 0;
        long totalFiles = 0;

        // 合并所有提交信息
        for (SnowflakeFileCommitInfo commitInfo : commitInfos) {
            // 合并分区文件
            commitInfo
                    .getAllPartitionFiles()
                    .forEach(
                            (partitionId, files) -> {
                                allPartitionFiles
                                        .computeIfAbsent(partitionId, k -> new ArrayList<>())
                                        .addAll(files);
                            });

            // 合并统计信息
            totalRows += commitInfo.getTotalRows();
            totalFiles += commitInfo.getTotalFiles();
        }

        SnowflakeFileAggCommitInfo aggCommitInfo =
                new SnowflakeFileAggCommitInfo(allPartitionFiles, totalRows, totalFiles);

        log.info(
                "Combined {} commit infos into {} files ({} rows)",
                commitInfos.size(),
                totalFiles,
                totalRows);

        return aggCommitInfo;
    }

    @Override
    public void abort(List<SnowflakeFileAggCommitInfo> aggregatedCommitInfos) throws Exception {
        if (aggregatedCommitInfos == null || aggregatedCommitInfos.isEmpty()) {
            return;
        }

        log.warn("Aborting {} aggregated commit infos", aggregatedCommitInfos.size());
        cleanupLocalFiles(collectAllS3Files(mergeCommitInfos(aggregatedCommitInfos)));

        // 如果需要清理 S3 文件，可以在这里实现
        // 目前依赖于 PURGE_AFTER_COPY 配置，Snowflake 会自动清理
    }

    @Override
    public void close() throws IOException {
        if (snowflakeClient != null) {
            try {
                snowflakeClient.close();
            } catch (SQLException e) {
                throw new IOException("Failed to close Snowflake client", e);
            }
        }
    }

    /** 确保连接到 Snowflake */
    private void ensureConnected() throws IOException {
        if (snowflakeClient == null) {
            snowflakeClient = new SnowflakeCopyIntoClient(config);
            try {
                snowflakeClient.connect();
                snowflakeClient.createFileFormatIfNotExists();
            } catch (SQLException e) {
                throw new IOException("Failed to connect to Snowflake", e);
            }
        }
    }

    /** 合并多个聚合提交信息 */
    private SnowflakeFileAggCommitInfo mergeCommitInfos(
            List<SnowflakeFileAggCommitInfo> commitInfos) {
        if (commitInfos.size() == 1) {
            return commitInfos.get(0);
        }

        Map<String, List<String>> allPartitionFiles = new HashMap<>();
        long totalRows = 0;
        long totalFiles = 0;

        for (SnowflakeFileAggCommitInfo commitInfo : commitInfos) {
            // 合并分区文件
            commitInfo
                    .getAllPartitionFiles()
                    .forEach(
                            (partitionId, files) -> {
                                allPartitionFiles
                                        .computeIfAbsent(partitionId, k -> new ArrayList<>())
                                        .addAll(files);
                            });

            // 合并统计信息
            totalRows += commitInfo.getTotalRows();
            totalFiles += commitInfo.getTotalFiles();
        }

        return new SnowflakeFileAggCommitInfo(allPartitionFiles, totalRows, totalFiles);
    }

    /** 收集所有 S3 文件 */
    private List<String> collectAllS3Files(SnowflakeFileAggCommitInfo commitInfo) {
        return commitInfo.getAllPartitionFiles().values().stream()
                .flatMap(files -> files.stream())
                .collect(Collectors.toList());
    }

    private void cleanupLocalFiles(List<String> filePaths) {
        if (!config.isLocalFileStagingBackend()) {
            return;
        }
        for (String filePath : filePaths) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                log.warn("Failed to delete local staging file: {}", filePath, e);
            }
        }
    }
}
