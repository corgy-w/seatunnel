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

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

/** S3错误诊断测试 用于诊断S3 400错误的具体原因 */
@Slf4j
public class S3ErrorDiagnosisTest {

    @Test
    public void diagnoseS3Connection() {
        log.info("=== S3连接错误诊断 ===");
        log.info("配置信息:");
        log.info("  桶: {}", S3TestConfig.S3_BUCKET);
        log.info("  区域: {}", S3TestConfig.S3_REGION);
        log.info("  端点: {}", S3TestConfig.S3_ENDPOINT);
        log.info(
                "  访问密钥: {}...",
                S3TestConfig.ACCESS_KEY.substring(
                        0, Math.min(8, S3TestConfig.ACCESS_KEY.length())));

        // 测试不同的桶名称格式
        String[] bucketFormats = {
            S3TestConfig.S3_BUCKET, // "s3a://wt-auto-bucket"
            "wt-auto-bucket", // 纯桶名称
            "s3://wt-auto-bucket", // s3://格式
            "wt-auto-bucket/", // 带尾部斜杠
            "s3a://wt-auto-bucket/" // 带前缀和尾部斜杠
        };

        for (String bucketFormat : bucketFormats) {
            testBucketFormat(bucketFormat);
        }
    }

    private void testBucketFormat(String bucketName) {
        log.info("\n--- 测试桶格式: {} ---", bucketName);

        try {
            // 清理桶名称
            String cleanBucketName = cleanBucketName(bucketName);
            log.info("清理后的桶名称: {}", cleanBucketName);

            // 创建S3客户端
            AwsBasicCredentials awsCredentials =
                    AwsBasicCredentials.create(S3TestConfig.ACCESS_KEY, S3TestConfig.SECRET_KEY);

            S3Client s3Client =
                    S3Client.builder()
                            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                            .region(Region.of(S3TestConfig.S3_REGION))
                            .endpointOverride(URI.create("https://" + S3TestConfig.S3_ENDPOINT))
                            .build();

            // 测试桶访问
            HeadBucketRequest headBucketRequest =
                    HeadBucketRequest.builder().bucket(cleanBucketName).build();

            log.info("发送HeadBucket请求...");
            HeadBucketResponse response = s3Client.headBucket(headBucketRequest);

            log.info("✅ 成功! 桶格式正确");
            log.info("  响应: {}", response);

            s3Client.close();

        } catch (S3Exception e) {
            log.error("❌ S3错误:");
            log.error("  错误代码: {}", e.awsErrorDetails().errorCode());
            log.error("  错误消息: {}", e.awsErrorDetails().errorMessage());
            log.error("  状态码: {}", e.statusCode());
            log.error("  请求ID: {}", e.requestId());
            log.error("  扩展请求ID: {}", e.extendedRequestId());

            // 分析具体错误
            analyzeS3Error(e);

        } catch (Exception e) {
            log.error("❌ 其他错误:");
            log.error("  异常类型: {}", e.getClass().getSimpleName());
            log.error("  错误消息: {}", e.getMessage());
            log.error("  堆栈跟踪:", e);
        }
    }

    private void analyzeS3Error(S3Exception e) {
        String errorCode = e.awsErrorDetails().errorCode();
        String errorMessage = e.awsErrorDetails().errorMessage();
        int statusCode = e.statusCode();

        log.info("\n--- 错误分析 ---");
        log.info("状态码: {}", statusCode);
        log.info("错误代码: {}", errorCode);
        log.info("错误消息: {}", errorMessage);

        if (statusCode == 400) {
            log.info("400错误可能的原因:");
            log.info("1. 桶名称格式不正确");
            log.info("2. 桶不存在");
            log.info("3. 区域不匹配");
            log.info("4. 访问密钥无效");
            log.info("5. 端点配置错误");

            if (errorMessage != null) {
                if (errorMessage.contains("not valid")) {
                    log.info("→ 桶名称格式问题");
                } else if (errorMessage.contains("not exist")) {
                    log.info("→ 桶不存在");
                } else if (errorMessage.contains("InvalidAccessKeyId")) {
                    log.info("→ 访问密钥无效");
                } else if (errorMessage.contains("SignatureDoesNotMatch")) {
                    log.info("→ 签名不匹配（密钥错误）");
                }
            }
        } else if (statusCode == 403) {
            log.info("403错误: 权限不足");
        } else if (statusCode == 404) {
            log.info("404错误: 桶不存在");
        } else {
            log.info("其他状态码: {}", statusCode);
        }
    }

    /** 清理桶名称，移除s3://或s3a://前缀和尾部斜杠 */
    private String cleanBucketName(String bucketName) {
        if (bucketName == null) {
            return null;
        }

        // 移除s3://或s3a://前缀
        String cleaned = bucketName.replaceFirst("^s3[an]?://", "");

        // 移除尾部斜杠
        cleaned = cleaned.replaceFirst("/$", "");

        return cleaned;
    }
}
