package org.apache.seatunnel.connectors.seatunnel.hive.source;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigFactory;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.connectors.seatunnel.hive.BaseHiveTest;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.List;

class HiveSourceTest extends BaseHiveTest {

    @Test
    void getProducedCatalogTables() throws FileNotFoundException, URISyntaxException {
        String path = getTestConfigFile("/hive.conf");
        Config config = ConfigFactory.parseFile(new File(path));
        ReadonlyConfig readonlyConfig = ReadonlyConfig.fromConfig(config);
        HiveSource hiveSource = new HiveSource(readonlyConfig);
        List<CatalogTable> producedCatalogTables = hiveSource.getProducedCatalogTables();
        System.out.println(producedCatalogTables);
    }
}
