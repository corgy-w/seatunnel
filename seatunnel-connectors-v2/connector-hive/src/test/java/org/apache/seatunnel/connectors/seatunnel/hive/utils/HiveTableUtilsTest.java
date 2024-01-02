package org.apache.seatunnel.connectors.seatunnel.hive.utils;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;
import org.apache.seatunnel.connectors.seatunnel.hive.sink.HiveSink;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

class HiveTableUtilsTest {

    @Disabled
    @Test
    void getTableInfo() throws FileNotFoundException, URISyntaxException {
        String path = getTestConfigFile("/hive.conf");
        Config config = ConfigFactory.parseFile(new File(path));
        ReadonlyConfig readonlyConfig = ReadonlyConfig.fromConfig(config);

        path = getTestConfigFile("/simple.schema.conf");
        config = ConfigFactory.parseFile(new File(path));
        CatalogTable catalogTable = CatalogTableUtil.buildWithConfig(config);

        HiveSink hiveSink = new HiveSink(readonlyConfig, catalogTable);
        System.out.println(hiveSink);
    }

    public static String getTestConfigFile(String configFile)
            throws FileNotFoundException, URISyntaxException {
        URL resource = HiveTableUtilsTest.class.getResource(configFile);
        if (resource == null) {
            throw new FileNotFoundException("Can't find config file: " + configFile);
        }
        return Paths.get(resource.toURI()).toString();
    }
}
