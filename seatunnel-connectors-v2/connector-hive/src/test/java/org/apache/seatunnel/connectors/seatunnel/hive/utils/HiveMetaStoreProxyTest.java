package org.apache.seatunnel.connectors.seatunnel.hive.utils;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.seatunnel.hive.BaseHiveTest;

import org.apache.hadoop.hive.metastore.api.Table;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;

class HiveMetaStoreProxyTest extends BaseHiveTest {

    @Disabled
    @Test
    void getTable() throws FileNotFoundException, URISyntaxException {
        String path = getTestConfigFile("/hive.conf");
        Config config = ConfigFactory.parseFile(new File(path));
        ReadonlyConfig readonlyConfig = ReadonlyConfig.fromConfig(config);
        Table table =
                HiveMetaStoreProxy.getInstance(readonlyConfig).getTable("default", "czjtest_03");
        System.out.println(table);
    }
}
