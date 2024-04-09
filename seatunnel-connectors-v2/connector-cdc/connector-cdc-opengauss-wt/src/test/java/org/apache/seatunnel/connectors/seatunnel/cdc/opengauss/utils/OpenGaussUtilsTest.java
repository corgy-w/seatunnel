package org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.utils;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.debezium.relational.TableId;

public class OpenGaussUtilsTest {
    @Test
    public void testSplitScanQuery() {
        String splitScanSQL =
                OpenGaussUtils.buildSplitScanQuery(
                        TableId.parse("db1.schema1.table1"),
                        new SeaTunnelRowType(
                                new String[] {"id"}, new SeaTunnelDataType[] {BasicType.LONG_TYPE}),
                        false,
                        false);
        Assertions.assertEquals(
                "SELECT * FROM \"schema1\".\"table1\" WHERE \"id\" >= ? AND NOT (\"id\" = ?) AND \"id\" <= ?",
                splitScanSQL);

        splitScanSQL =
                OpenGaussUtils.buildSplitScanQuery(
                        TableId.parse("db1.schema1.table1"),
                        new SeaTunnelRowType(
                                new String[] {"id"}, new SeaTunnelDataType[] {BasicType.LONG_TYPE}),
                        true,
                        true);
        Assertions.assertEquals("SELECT * FROM \"schema1\".\"table1\"", splitScanSQL);

        splitScanSQL =
                OpenGaussUtils.buildSplitScanQuery(
                        TableId.parse("db1.schema1.table1"),
                        new SeaTunnelRowType(
                                new String[] {"id"}, new SeaTunnelDataType[] {BasicType.LONG_TYPE}),
                        true,
                        false);
        Assertions.assertEquals(
                "SELECT * FROM \"schema1\".\"table1\" WHERE \"id\" <= ? AND NOT (\"id\" = ?)",
                splitScanSQL);

        splitScanSQL =
                OpenGaussUtils.buildSplitScanQuery(
                        TableId.parse("db1.schema1.table1"),
                        new SeaTunnelRowType(
                                new String[] {"id"}, new SeaTunnelDataType[] {BasicType.LONG_TYPE}),
                        false,
                        true);
        Assertions.assertEquals(
                "SELECT * FROM \"schema1\".\"table1\" WHERE \"id\" >= ?", splitScanSQL);
    }
}
