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

package org.apache.seatunnel.connectors.seatunnel.snowflakefile.config;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;

import java.util.HashMap;
import java.util.Map;

public class S3TestConfig {

    // S3配置信息
    public static final String S3_BUCKET = "s3a://wt-auto-bucket";
    public static final String S3_REGION = "cn-north-1";
    public static final String S3_ENDPOINT = "s3.cn-north-1.amazonaws.com.cn";
    public static final String ACCESS_KEY = "AKIAYYUV5DMXNWIDLUEB";
    public static final String SECRET_KEY = "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";
    public static final String CREDENTIALS_PROVIDER =
            "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider";

    // Hadoop S3配置
    public static final String HADOOP_S3_IMPL = "org.apache.hadoop.fs.s3a.S3AFileSystem";
    public static final String S3A_IMPL_DISABLE_CACHE = "true";

    // Snowflake测试配置
    public static final String SNOWFLAKE_ACCOUNT = "test_account";
    public static final String SNOWFLAKE_WAREHOUSE = "test_warehouse";
    public static final String SNOWFLAKE_DATABASE = "test_database";
    public static final String SNOWFLAKE_SCHEMA = "test_schema";
    public static final String SNOWFLAKE_TABLE = "test_table";
    public static final String SNOWFLAKE_USER = "test_user";
    public static final String SNOWFLAKE_PASSWORD = "test_password";

    /** 创建完整的S3测试配置 */
    public static Config createS3TestConfig() {
        Map<String, Object> configMap = new HashMap<>();

        // Snowflake连接配置
        configMap.put("account", SNOWFLAKE_ACCOUNT);
        configMap.put("warehouse", SNOWFLAKE_WAREHOUSE);
        configMap.put("database", SNOWFLAKE_DATABASE);
        configMap.put("schema", SNOWFLAKE_SCHEMA);
        configMap.put("table", SNOWFLAKE_TABLE);
        configMap.put("user", SNOWFLAKE_USER);
        configMap.put("password", SNOWFLAKE_PASSWORD);

        // S3配置
        configMap.put("s3_bucket", S3_BUCKET);
        configMap.put("s3_protocol", "s3china"); // 使用中国区域协议
        configMap.put("s3_region", S3_REGION);
        configMap.put("s3_key_prefix", "seatunnel-test/");
        configMap.put("aws_access_key_id", ACCESS_KEY);
        configMap.put("aws_secret_access_key", SECRET_KEY);

        // 文件格式配置
        configMap.put("file_format", "CSV");
        configMap.put("field_delimiter", ",");
        configMap.put("record_delimiter", "\\n");
        configMap.put("file_extension", ".csv");

        // 性能配置
        configMap.put("buffer_size", 1048576); // 1MB
        configMap.put("max_file_size", 10485760); // 10MB (测试用小文件)
        configMap.put("purge_after_copy", false); // 测试时不删除文件

        // 时间格式
        configMap.put("time_format", "HH24:MI:SS");
        configMap.put("date_format", "YYYY-MM-DD");
        configMap.put("timestamp_format", "YYYY-MM-DD HH24:MI:SS.FF3");

        // S3A特殊配置
        configMap.put("fs.s3a.endpoint", S3_ENDPOINT);
        configMap.put("fs.s3a.aws.credentials.provider", CREDENTIALS_PROVIDER);
        configMap.put("fs.s3a.impl.disable.cache", S3A_IMPL_DISABLE_CACHE);

        return ConfigFactory.parseMap(configMap);
    }

    /** 创建S3连接测试配置（仅S3相关） */
    public static Config createS3ConnectionTestConfig() {
        Map<String, Object> configMap = new HashMap<>();

        configMap.put("s3_bucket", S3_BUCKET);
        configMap.put("s3_protocol", "s3china"); // 使用中国区域协议
        configMap.put("s3_region", S3_REGION);
        configMap.put("aws_access_key_id", ACCESS_KEY);
        configMap.put("aws_secret_access_key", SECRET_KEY);
        configMap.put("fs.s3a.endpoint", S3_ENDPOINT);
        configMap.put("fs.s3a.aws.credentials.provider", CREDENTIALS_PROVIDER);
        configMap.put("fs.s3a.impl.disable.cache", S3A_IMPL_DISABLE_CACHE);

        return ConfigFactory.parseMap(configMap);
    }

    /** 获取S3A文件系统URL */
    public static String getS3AUrl(String keyPrefix) {
        return String.format("s3a://%s/%s", S3_BUCKET.replace("s3a://", ""), keyPrefix);
    }

    /** 验证S3配置是否完整 */
    public static boolean isS3ConfigValid() {
        // 仅检查基本非空，不再检测示例配置
        // 因为您的配置是真实的S3配置
        return S3_BUCKET != null
                && !S3_BUCKET.isEmpty()
                && ACCESS_KEY != null
                && !ACCESS_KEY.isEmpty()
                && SECRET_KEY != null
                && !SECRET_KEY.isEmpty();
    }
}
