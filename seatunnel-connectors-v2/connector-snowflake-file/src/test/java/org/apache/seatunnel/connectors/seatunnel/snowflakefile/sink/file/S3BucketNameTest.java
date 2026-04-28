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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S3桶名称处理测试 验证桶名称的正确格式 */
@Slf4j
public class S3BucketNameTest {

    @Test
    public void testBucketNameFormat() {
        log.info("=== 测试S3桶名称格式 ===");

        String originalBucket = S3TestConfig.S3_BUCKET;
        log.info("原始桶配置: {}", originalBucket);

        // 测试不同的桶名称格式
        String[] testCases = {
            "s3a://wt-auto-bucket",
            "wt-auto-bucket",
            "s3://wt-auto-bucket",
            "wt-auto-bucket/",
            "s3a://wt-auto-bucket/"
        };

        for (String testCase : testCases) {
            String cleanedBucket = cleanBucketName(testCase);
            log.info("输入: {} -> 输出: {}", testCase, cleanedBucket);

            // 验证清理后的桶名称
            assertFalse(cleanedBucket.startsWith("s3://"), "桶名称不应以s3://开头");
            assertFalse(cleanedBucket.startsWith("s3a://"), "桶名称不应以s3a://开头");
            assertFalse(cleanedBucket.endsWith("/"), "桶名称不应以/结尾");
            assertTrue(cleanedBucket.length() > 0, "桶名称不应为空");
        }
    }

    @Test
    public void testCurrentConfiguration() {
        log.info("=== 测试当前配置 ===");

        String currentBucket = S3TestConfig.S3_BUCKET;
        String cleanedBucket = cleanBucketName(currentBucket);

        log.info("当前配置: {}", currentBucket);
        log.info("清理后: {}", cleanedBucket);

        assertEquals("wt-auto-bucket", cleanedBucket, "当前配置的桶名称应清理为wt-auto-bucket");
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
