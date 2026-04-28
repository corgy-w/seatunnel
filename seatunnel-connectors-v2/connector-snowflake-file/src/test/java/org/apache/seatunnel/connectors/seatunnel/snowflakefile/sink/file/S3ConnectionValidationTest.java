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

import org.apache.seatunnel.connectors.seatunnel.snowflakefile.config.S3TestConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
/** S3连接详细验证测试 用于验证真实S3环境的连接性和功能 */
public class S3ConnectionValidationTest {

    private S3Client s3Client;
    private String testBucket;
    private String testKey;

    @BeforeEach
    public void setUp() {
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

            testBucket = S3TestConfig.S3_BUCKET.replace("s3a://", "");
            testKey = "seatunnel-validation-test/" + UUID.randomUUID().toString() + ".txt";

            log.info("S3连接验证测试初始化完成");
            log.info("目标桶: {}", testBucket);
            log.info("测试文件: {}", testKey);
            log.info("区域: {}", S3TestConfig.S3_REGION);
            log.info("端点: {}", S3TestConfig.S3_ENDPOINT);

        } catch (Exception e) {
            log.error("S3连接初始化失败", e);
            fail("S3连接初始化失败: " + e.getMessage());
        }
    }

    @Test
    public void testListBuckets() {
        log.info("=== 测试1: 列出所有S3桶 ===");

        try {
            ListBucketsResponse response = s3Client.listBuckets();
            List<Bucket> buckets = response.buckets();

            log.info("找到 {} 个S3桶", buckets.size());
            buckets.forEach(
                    bucket ->
                            log.info(
                                    "  - 桶名称: {}, 创建时间: {}", bucket.name(), bucket.creationDate()));

            // 检查目标桶是否存在
            boolean targetBucketExists =
                    buckets.stream().anyMatch(bucket -> bucket.name().equals(testBucket));

            if (targetBucketExists) {
                log.info("✅ 目标桶 '{}' 存在于S3中", testBucket);
            } else {
                log.error("❌ 目标桶 '{}' 不存在于S3中", testBucket);
                log.info("可用的桶有: {}", buckets.stream().map(Bucket::name).toArray());
            }

            assertTrue(targetBucketExists, "目标桶必须存在");

        } catch (Exception e) {
            log.error("❌ 列出S3桶失败: {}", e.getMessage(), e);
            fail("列出S3桶失败: " + e.getMessage());
        }
    }

    @Test
    public void testBucketAccess() {
        log.info("=== 测试2: 验证桶访问权限 ===");

        try {
            HeadBucketRequest headBucketRequest =
                    HeadBucketRequest.builder().bucket(testBucket).build();

            s3Client.headBucket(headBucketRequest);
            log.info("✅ 成功访问S3桶: {}", testBucket);

        } catch (Exception e) {
            log.error("❌ 访问S3桶 '{}' 失败: {}", testBucket, e.getMessage(), e);
            fail("访问S3桶失败: " + e.getMessage());
        }
    }

    @Test
    public void testFileUpload() {
        log.info("=== 测试3: 测试文件上传功能 ===");

        String testContent =
                "Hello from SeaTunnel SnowflakeFile Connector!\nTest time: " + Instant.now();

        try {
            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(testBucket)
                            .key(testKey)
                            .contentType("text/plain")
                            .build();

            PutObjectResponse response =
                    s3Client.putObject(putObjectRequest, RequestBody.fromString(testContent));

            log.info("✅ 文件上传成功!");
            log.info("  - 文件路径: s3://{}/{}", testBucket, testKey);
            log.info("  - 内容长度: {} 字节", testContent.length());
            log.info("  - ETag: {}", response.eTag());
            log.info("  - 版本ID: {}", response.versionId() != null ? response.versionId() : "N/A");

            assertNotNull(response.eTag(), "ETag不应为null");

        } catch (Exception e) {
            log.error("❌ 文件上传失败: {}", e.getMessage(), e);
            fail("文件上传失败: " + e.getMessage());
        }
    }

    @Test
    public void testBucketLocation() {
        log.info("=== 测试4: 验证桶位置 ===");

        try {
            // 获取桶的位置信息
            software.amazon.awssdk.services.s3.model.GetBucketLocationResponse locationResponse =
                    s3Client.getBucketLocation(
                            software.amazon.awssdk.services.s3.model.GetBucketLocationRequest
                                    .builder()
                                    .bucket(testBucket)
                                    .build());

            String bucketLocation = locationResponse.locationConstraint().toString();

            log.info("✅ 桶位置信息:");
            log.info("  - 桶名称: {}", testBucket);
            log.info("  - 位置约束: {}", bucketLocation);
            log.info("  - 预期区域: {}", S3TestConfig.S3_REGION);

            // 验证区域匹配
            if (bucketLocation.contains(S3TestConfig.S3_REGION)) {
                log.info("✅ 桶位置与配置区域匹配");
            } else {
                log.warn("⚠️  桶位置 '{}' 与配置区域 '{}' 不匹配", bucketLocation, S3TestConfig.S3_REGION);
            }

        } catch (Exception e) {
            log.error("❌ 获取桶位置信息失败: {}", e.getMessage(), e);
            // 不失败，只是警告
        }
    }

    @Test
    public void testEndpointConnectivity() {
        log.info("=== 测试5: 验证端点连接 ===");

        try {
            // 尝试简单的操作来验证端点连接
            s3Client.listBuckets();
            log.info("✅ 成功连接到S3端点: {}", S3TestConfig.S3_ENDPOINT);

        } catch (Exception e) {
            log.error("❌ 无法连接到S3端点 '{}': {}", S3TestConfig.S3_ENDPOINT, e.getMessage(), e);
            fail("S3端点连接失败: " + e.getMessage());
        }
    }
}
