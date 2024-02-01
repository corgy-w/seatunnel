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

package org.apache.seatunnel.connectors.dolphindb.sink.writter;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig;

import com.xxdb.multithreadedtablewriter.MultithreadedTableWriter;

import java.util.List;

import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.BATCH_SIZE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.KEY_COL_NAMES;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.THROTTLE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.USE_SSL;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.WRITE_MODE;

public class MultithreadedTableWriterFactory {

    public static MultithreadedTableWriter createMultithreadedTableWriter(
            ReadonlyConfig pluginConfig) throws Exception {
        List<String> addresses = pluginConfig.get(DolphinDBConfig.ADDRESS);
        String address = addresses.get(0);
        String host = address.substring(0, address.lastIndexOf(":"));
        int port = Integer.parseInt(address.substring(address.lastIndexOf(":") + 1));

        int[] compressType = null;
        if (pluginConfig.get(DolphinDBConfig.COMPRESS_TYPE) != null) {
            List<Integer> compressTypeList = pluginConfig.get(DolphinDBConfig.COMPRESS_TYPE);
            compressType = compressTypeList.stream().mapToInt(Integer::intValue).toArray();
        }
        String partitionColumn = null;
        if (pluginConfig.get(DolphinDBConfig.PARTITION_COLUMN) != null) {
            partitionColumn = pluginConfig.get(DolphinDBConfig.PARTITION_COLUMN);
        }
        String[] pModelOption = null;
        if (pluginConfig.get(KEY_COL_NAMES) != null) {
            pModelOption = pluginConfig.get(KEY_COL_NAMES).toArray(new String[0]);
        }

        return new MultithreadedTableWriter(
                host,
                port,
                pluginConfig.get(DolphinDBConfig.USER),
                pluginConfig.get(DolphinDBConfig.PASSWORD),
                pluginConfig.get(DolphinDBConfig.DATABASE),
                pluginConfig.get(DolphinDBConfig.TABLE),
                pluginConfig.get(USE_SSL),
                address.length() > 1,
                addresses.toArray(new String[0]),
                pluginConfig.get(BATCH_SIZE),
                pluginConfig.get(THROTTLE),
                1,
                partitionColumn,
                compressType,
                pluginConfig.get(WRITE_MODE),
                pModelOption);
    }
}
