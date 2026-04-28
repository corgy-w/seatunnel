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

package org.apache.seatunnel.connectors.seatunnel.snowflakefile.sink.file;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.snowflakefile.config.SnowflakeFileConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class S3FileWriterTest {

    @Mock private S3Client s3Client;

    @Mock private SnowflakeFileConfig config;

    private S3FileWriter s3FileWriter;
    private SeaTunnelRowType rowType;

    @BeforeEach
    public void setUp() {
        // 配置模拟
        when(config.getS3Bucket()).thenReturn("test-bucket");
        when(config.getS3KeyPrefix()).thenReturn("test-prefix/");
        when(config.getFieldDelimiter()).thenReturn(",");
        when(config.getRecordDelimiter()).thenReturn("\n");
        when(config.getFileExtension()).thenReturn(".csv");
        when(config.getMaxFileSize()).thenReturn(1024 * 1024L); // 1MB

        // 创建行类型
        rowType =
                new SeaTunnelRowType(
                        new String[] {"id", "name", "age"},
                        new BasicType[] {
                            BasicType.LONG_TYPE, BasicType.STRING_TYPE, BasicType.INT_TYPE
                        });

        // Mock S3 响应 - 只在需要时使用
        // PutObjectResponse response = mock(PutObjectResponse.class);
        // when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        //         .thenReturn(response);

        s3FileWriter = new S3FileWriter(s3Client, config, rowType);
    }

    @Test
    public void testWriteRow() throws IOException {
        // Mock S3 响应
        PutObjectResponse response = mock(PutObjectResponse.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);

        // 创建测试数据
        SeaTunnelRow row = new SeaTunnelRow(3);
        row.setField(0, 1L);
        row.setField(1, "John Doe");
        row.setField(2, 25);

        // 写入数据
        s3FileWriter.writeRow(row, "partition1");

        // 刷新数据
        s3FileWriter.flushAll();

        // 验证 S3 上传被调用
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // 验证上传的文件
        List<String> uploadedFiles = s3FileWriter.getUploadedFiles("partition1");
        assertFalse(uploadedFiles.isEmpty());
        assertTrue(uploadedFiles.get(0).contains("s3://test-bucket/test-prefix/"));
    }

    @Test
    public void testWriteMultipleRows() throws IOException {
        // 创建多个测试数据
        for (int i = 0; i < 10; i++) {
            SeaTunnelRow row = new SeaTunnelRow(3);
            row.setField(0, (long) i);
            row.setField(1, "User " + i);
            row.setField(2, 20 + i);
            s3FileWriter.writeRow(row, "partition1");
        }

        // 刷新数据
        s3FileWriter.flushAll();

        // 验证 S3 上传被调用
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // 验证上传的文件数量
        List<String> uploadedFiles = s3FileWriter.getUploadedFiles("partition1");
        assertEquals(1, uploadedFiles.size());
    }

    @Test
    public void testWriteRowWithNullValues() throws IOException {
        // 创建包含 null 值的测试数据
        SeaTunnelRow row = new SeaTunnelRow(3);
        row.setField(0, 1L);
        row.setField(1, null); // null 值
        row.setField(2, 25);

        // 写入数据
        s3FileWriter.writeRow(row, "partition1");
        s3FileWriter.flushAll();

        // 验证 S3 上传被调用
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testWriteRowWithSpecialCharacters() throws IOException {
        // 创建包含特殊字符的测试数据
        SeaTunnelRow row = new SeaTunnelRow(3);
        row.setField(0, 1L);
        row.setField(1, "John, \"Doe\""); // 包含逗号和引号
        row.setField(2, 25);

        // 写入数据
        s3FileWriter.writeRow(row, "partition1");
        s3FileWriter.flushAll();

        // 验证 S3 上传被调用
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testMultiplePartitions() throws IOException {
        // 向不同分区写入数据
        for (int i = 0; i < 5; i++) {
            SeaTunnelRow row = new SeaTunnelRow(3);
            row.setField(0, (long) i);
            row.setField(1, "User " + i);
            row.setField(2, 20 + i);
            s3FileWriter.writeRow(row, "partition" + (i % 3)); // 3个分区
        }

        // 刷新数据
        s3FileWriter.flushAll();

        // 验证每个分区都有文件上传
        for (int i = 0; i < 3; i++) {
            List<String> uploadedFiles = s3FileWriter.getUploadedFiles("partition" + i);
            assertFalse(uploadedFiles.isEmpty());
        }
    }

    @Test
    public void testCleanupFiles() {
        // 创建测试文件列表
        List<String> filesToCleanup =
                Arrays.asList(
                        "s3://test-bucket/test-prefix/file1.csv",
                        "s3://test-bucket/test-prefix/file2.csv");

        // 清理文件
        s3FileWriter.cleanupFiles(filesToCleanup);

        // 验证删除被调用
        verify(s3Client, times(2))
                .deleteObject(
                        any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    public void testClose() throws IOException {
        // 写入一些数据
        SeaTunnelRow row = new SeaTunnelRow(3);
        row.setField(0, 1L);
        row.setField(1, "Test User");
        row.setField(2, 30);
        s3FileWriter.writeRow(row, "partition1");

        // 关闭写入器
        s3FileWriter.close();

        // 验证刷新和关闭被调用
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
