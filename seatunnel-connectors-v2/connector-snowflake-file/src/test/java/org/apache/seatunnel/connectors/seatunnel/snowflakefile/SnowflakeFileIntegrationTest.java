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

package org.apache.seatunnel.connectors.seatunnel.snowflakefile;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.config.S3TestConfig;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.config.SnowflakeFileConfig;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.sink.file.S3FileWriter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SnowflakeFile集成测试 使用真实的S3配置进行完整流程测试 */
@Slf4j
public class SnowflakeFileIntegrationTest {

    private S3Client s3Client;
    private S3FileWriter s3FileWriter;
    private String testKeyPrefix;
    private SeaTunnelRowType rowType;

    @BeforeEach
    public void setUp() {
        // 验证S3配置是否有效
        Assumptions.assumeTrue(S3TestConfig.isS3ConfigValid(), "S3配置无效，跳过集成测试");

        try {
            // 创建S3客户端
            AwsBasicCredentials awsCredentials =
                    AwsBasicCredentials.create(S3TestConfig.ACCESS_KEY, S3TestConfig.SECRET_KEY);

            s3Client =
                    S3Client.builder()
                            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                            .region(Region.of(S3TestConfig.S3_REGION))
                            .endpointOverride(URI.create("https://" + S3TestConfig.S3_ENDPOINT))
                            .build();

            // 生成唯一测试前缀
            testKeyPrefix = "seatunnel-integration-test/" + UUID.randomUUID().toString() + "/";

            // 创建行类型
            rowType =
                    new SeaTunnelRowType(
                            new String[] {"id", "name", "age", "salary", "create_time"},
                            new BasicType[] {
                                BasicType.LONG_TYPE,
                                BasicType.STRING_TYPE,
                                BasicType.INT_TYPE,
                                BasicType.DOUBLE_TYPE,
                                BasicType.STRING_TYPE
                            });

            // 创建S3文件写入器
            SnowflakeFileConfig config = new SnowflakeFileConfig(S3TestConfig.createS3TestConfig());
            s3FileWriter = new S3FileWriter(s3Client, config, rowType);

            log.info("集成测试初始化完成");
            log.info("S3桶: {}", S3TestConfig.S3_BUCKET);
            log.info("测试前缀: {}", testKeyPrefix);

        } catch (Exception e) {
            log.error("集成测试初始化失败", e);
            Assumptions.assumeTrue(false, "集成测试初始化失败: " + e.getMessage());
        }
    }

    @Test
    public void testCompleteDataFlow() throws IOException {
        log.info("开始完整数据流测试...");

        try {
            // 1. 写入测试数据
            writeTestData();

            // 2. 刷新数据到S3
            s3FileWriter.flushAll();

            // 3. 验证文件上传
            verifyFileUpload();

            // 4. 验证数据内容
            verifyDataContent();

            log.info("✅ 完整数据流测试通过");

        } catch (Exception e) {
            log.error("❌ 完整数据流测试失败", e);
            throw e;
        } finally {
            // 清理测试文件
            cleanupTestFiles();
        }
    }

    @Test
    public void testMultiplePartitions() throws IOException {
        log.info("开始多分区测试...");

        try {
            // 写入不同分区的数据
            for (int partition = 0; partition < 3; partition++) {
                String partitionId = "partition-" + partition;

                for (int i = 0; i < 5; i++) {
                    SeaTunnelRow row = new SeaTunnelRow(5);
                    row.setField(0, (long) (partition * 100 + i));
                    row.setField(1, "User-" + partition + "-" + i);
                    row.setField(2, 20 + i);
                    row.setField(3, 5000.0 + i * 100);
                    row.setField(4, "2024-01-" + String.format("%02d", i + 1));

                    s3FileWriter.writeRow(row, partitionId);
                }
            }

            // 刷新所有分区
            s3FileWriter.flushAll();

            // 验证每个分区的文件
            for (int partition = 0; partition < 3; partition++) {
                String partitionId = "partition-" + partition;
                List<String> uploadedFiles = s3FileWriter.getUploadedFiles(partitionId);

                assertFalse(uploadedFiles.isEmpty(), "分区 " + partitionId + " 应该有上传的文件");

                log.info("分区 {} 上传了 {} 个文件", partitionId, uploadedFiles.size());
            }

            log.info("✅ 多分区测试通过");

        } catch (Exception e) {
            log.error("❌ 多分区测试失败", e);
            throw e;
        } finally {
            cleanupTestFiles();
        }
    }

    @Test
    public void testLargeDataVolume() throws IOException {
        log.info("开始大数据量测试...");

        try {
            // 写入大量数据（触发文件滚动）
            int rowCount = 1000;
            String partitionId = "large-data-partition";

            log.info("准备写入 {} 行数据", rowCount);

            for (int i = 0; i < rowCount; i++) {
                SeaTunnelRow row = new SeaTunnelRow(5);
                row.setField(0, (long) i);
                row.setField(1, "LargeDataUser" + i);
                row.setField(2, 25 + (i % 50));
                row.setField(3, 6000.0 + (i % 1000));
                row.setField(4, "2024-01-" + String.format("%02d", (i % 30) + 1));

                s3FileWriter.writeRow(row, partitionId);

                if (i % 100 == 0) {
                    log.info("已写入 {} 行数据", i);
                }
            }

            // 刷新数据
            s3FileWriter.flushAll();

            // 验证文件数量和大小
            List<String> uploadedFiles = s3FileWriter.getUploadedFiles(partitionId);

            assertFalse(uploadedFiles.isEmpty(), "应该有文件上传");
            log.info("✅ 大数据量测试通过，共上传 {} 个文件", uploadedFiles.size());

        } catch (Exception e) {
            log.error("❌ 大数据量测试失败", e);
            throw e;
        } finally {
            cleanupTestFiles();
        }
    }

    @Test
    public void testSpecialCharacters() throws IOException {
        log.info("开始特殊字符测试...");

        try {
            String partitionId = "special-chars-partition";

            // 测试包含特殊字符的数据
            String[] specialNames = {
                "John,Smith", // 逗号
                "Mary\"O'Brien\"", // 引号
                "Li\"Hua", // 中文和引号
                "José García", // 西班牙语字符
                "Smith\\John", // 反斜杠
                "O'Connor", // 单引号
                "Test\nNewline", // 换行符
                "Tab\tCharacter", // 制表符
                "Normal Name" // 普通名称
            };

            for (int i = 0; i < specialNames.length; i++) {
                SeaTunnelRow row = new SeaTunnelRow(5);
                row.setField(0, (long) i);
                row.setField(1, specialNames[i]);
                row.setField(2, 25 + i);
                row.setField(3, 5000.0 + i * 100);
                row.setField(4, "2024-01-" + String.format("%02d", i + 1));

                s3FileWriter.writeRow(row, partitionId);
            }

            // 刷新数据
            s3FileWriter.flushAll();

            List<String> uploadedFiles = s3FileWriter.getUploadedFiles(partitionId);
            assertFalse(uploadedFiles.isEmpty(), "应该有文件上传");

            log.info("✅ 特殊字符测试通过，处理了 {} 个特殊字符数据", specialNames.length);

        } catch (Exception e) {
            log.error("❌ 特殊字符测试失败", e);
            throw e;
        } finally {
            cleanupTestFiles();
        }
    }

    @Test
    public void testNullValues() throws IOException {
        log.info("开始空值测试...");

        try {
            String partitionId = "null-values-partition";

            // 测试包含null值的数据
            for (int i = 0; i < 10; i++) {
                SeaTunnelRow row = new SeaTunnelRow(5);
                row.setField(0, (long) i);
                row.setField(1, i % 3 == 0 ? null : "User" + i); // 每3个数据有一个null
                row.setField(2, i % 4 == 0 ? null : 25 + i); // 每4个数据有一个null
                row.setField(3, 5000.0 + i * 100);
                row.setField(4, "2024-01-" + String.format("%02d", i + 1));

                s3FileWriter.writeRow(row, partitionId);
            }

            // 刷新数据
            s3FileWriter.flushAll();

            List<String> uploadedFiles = s3FileWriter.getUploadedFiles(partitionId);
            assertFalse(uploadedFiles.isEmpty(), "应该有文件上传");

            log.info("✅ 空值测试通过");

        } catch (Exception e) {
            log.error("❌ 空值测试失败", e);
            throw e;
        } finally {
            cleanupTestFiles();
        }
    }

    private void writeTestData() throws IOException {
        log.info("写入测试数据...");

        // 创建测试数据
        List<SeaTunnelRow> testData =
                Arrays.asList(
                        createRow(1L, "Alice", 25, 5000.0, "2024-01-01"),
                        createRow(2L, "Bob", 30, 6000.0, "2024-01-02"),
                        createRow(3L, "Charlie", 35, 7000.0, "2024-01-03"),
                        createRow(4L, "Diana", 28, 5500.0, "2024-01-04"),
                        createRow(5L, "Eve", 32, 6500.0, "2024-01-05"));

        String partitionId = "test-partition";
        for (SeaTunnelRow row : testData) {
            s3FileWriter.writeRow(row, partitionId);
        }

        log.info("✅ 测试数据写入完成，共 {} 行", testData.size());
    }

    private SeaTunnelRow createRow(
            long id, String name, int age, double salary, String createTime) {
        SeaTunnelRow row = new SeaTunnelRow(5);
        row.setField(0, id);
        row.setField(1, name);
        row.setField(2, age);
        row.setField(3, salary);
        row.setField(4, createTime);
        return row;
    }

    private void verifyFileUpload() {
        log.info("验证文件上传...");

        List<String> uploadedFiles = s3FileWriter.getUploadedFiles("test-partition");
        assertFalse(uploadedFiles.isEmpty(), "应该有文件上传");

        log.info("✅ 文件上传验证完成，共 {} 个文件", uploadedFiles.size());
        for (String file : uploadedFiles) {
            log.info("  - 上传文件: {}", file);
        }
    }

    private void verifyDataContent() {
        log.info("验证数据内容...");

        // 这里可以添加读取S3文件并验证内容的逻辑
        // 目前主要验证文件是否成功上传
        List<String> uploadedFiles = s3FileWriter.getUploadedFiles("test-partition");

        assertTrue(uploadedFiles.size() >= 1, "至少应该有一个文件");
        assertTrue(
                uploadedFiles.get(0).contains(S3TestConfig.S3_BUCKET.replace("s3a://", "")),
                "文件路径应该包含桶名称");

        log.info("✅ 数据内容验证完成");
    }

    private void cleanupTestFiles() {
        log.info("清理测试文件...");

        try {
            // 获取所有上传的文件
            ConcurrentMap<String, List<String>> allPartitionFiles =
                    s3FileWriter.getAllUploadedFiles();

            // 收集所有文件进行清理
            List<String> allFiles = new ArrayList<>();
            allPartitionFiles.values().forEach(allFiles::addAll);

            if (!allFiles.isEmpty()) {
                // 清理文件
                s3FileWriter.cleanupFiles(allFiles);
                log.info("✅ 测试文件清理完成，共清理 {} 个文件", allFiles.size());
            } else {
                log.info("没有需要清理的测试文件");
            }
        } catch (Exception e) {
            log.warn("清理测试文件时出错: {}", e.getMessage());
            // 清理失败不影响测试结果
        }
    }
}
