package org.apache.seatunnel.connectors.seatunnel.hive;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class BaseHiveTest {

    protected String getTestConfigFile(String configFile)
            throws FileNotFoundException, URISyntaxException {
        URL resource = BaseHiveTest.class.getResource(configFile);
        if (resource == null) {
            throw new FileNotFoundException("Can't find config file: " + configFile);
        }
        return Paths.get(resource.toURI()).toString();
    }
}
