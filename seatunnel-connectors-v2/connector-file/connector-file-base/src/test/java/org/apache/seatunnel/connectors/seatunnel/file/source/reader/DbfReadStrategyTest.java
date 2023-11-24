package org.apache.seatunnel.connectors.seatunnel.file.source.reader;

import org.apache.seatunnel.connectors.seatunnel.file.writer.OrcReadStrategyTest;

import org.junit.jupiter.api.Test;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

class DbfReadStrategyTest {

    @Test
    void read() throws URISyntaxException, IOException {
        URL dbfFile = OrcReadStrategyTest.class.getResource("/books.dbf");
        String dbfFilePath = Paths.get(dbfFile.toURI()).toString();
        try (FileInputStream file = new FileInputStream(dbfFilePath);
                DBFReader reader = new DBFReader(file)) {
            int fieldCount = reader.getFieldCount();
            DBFField[] dbfFields = new DBFField[fieldCount];
            System.out.println("********************** Header **********************");
            for (int i = 0; i < fieldCount; i++) {
                DBFField field = reader.getField(i);
                System.out.println(field.getName());
                dbfFields[i] = field;
            }
            System.out.println("********************** Header **********************");

            Object[] rowObjects;
            while ((rowObjects = reader.nextRecord()) != null) {
                System.out.println("********************** Row **********************");
                for (int i = 0; i < rowObjects.length; i++) {
                    Class<?> aClass = rowObjects[i] == null ? null : rowObjects[i].getClass();
                    System.out.println(
                            dbfFields[i].getType() + ": " + rowObjects[i] + ": " + aClass);
                }
                System.out.println("********************** Row **********************");
            }
        }
    }
}
