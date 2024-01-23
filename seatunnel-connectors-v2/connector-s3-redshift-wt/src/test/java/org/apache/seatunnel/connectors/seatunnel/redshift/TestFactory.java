package org.apache.seatunnel.connectors.seatunnel.redshift;

import org.apache.seatunnel.connectors.seatunnel.redshift.sink.S3RedshiftSinkFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestFactory {
    @Test
    public void testOptionRule() {
        Assertions.assertNotNull((new S3RedshiftSinkFactory()).optionRule());
    }
}
