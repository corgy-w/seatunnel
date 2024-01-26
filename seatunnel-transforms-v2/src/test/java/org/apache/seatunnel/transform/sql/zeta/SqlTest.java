package org.apache.seatunnel.transform.sql.zeta;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.transform.exception.TransformException;
import org.apache.seatunnel.transform.sql.SQLMultiCatalogTransform;
import org.apache.seatunnel.transform.sql.SQLTransformConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SqlTest {

    private static final CatalogTable DEFAULT_TABLE =
            CatalogTable.of(
                    TableIdentifier.of("mysql-1", "database-x", null, "table-x"),
                    TableSchema.builder()
                            .column(
                                    PhysicalColumn.of(
                                            "f1",
                                            BasicType.LONG_TYPE,
                                            null,
                                            false,
                                            null,
                                            null,
                                            "int unsigned",
                                            false,
                                            false,
                                            null,
                                            null,
                                            null))
                            .column(
                                    PhysicalColumn.of(
                                            "f2",
                                            BasicType.STRING_TYPE,
                                            10,
                                            false,
                                            null,
                                            null,
                                            "varchar(10)",
                                            false,
                                            false,
                                            null,
                                            null,
                                            null))
                            .column(
                                    PhysicalColumn.of(
                                            "f3",
                                            BasicType.STRING_TYPE,
                                            20,
                                            false,
                                            null,
                                            null,
                                            "varchar(20)",
                                            false,
                                            false,
                                            null,
                                            null,
                                            null))
                            .primaryKey(PrimaryKey.of("pk1", Arrays.asList("f1")))
                            .constraintKey(
                                    ConstraintKey.of(
                                            ConstraintKey.ConstraintType.UNIQUE_KEY,
                                            "uk1",
                                            Arrays.asList(
                                                    ConstraintKey.ConstraintKeyColumn.of(
                                                            "f2", ConstraintKey.ColumnSortType.ASC),
                                                    ConstraintKey.ConstraintKeyColumn.of(
                                                            "f3",
                                                            ConstraintKey.ColumnSortType.ASC))))
                            .build(),
                    Collections.emptyMap(),
                    Collections.singletonList("f2"),
                    null);

    @Test
    void testProduceNewCatalogTable() {
        final SQLTransformConfig.TableTransforms tableTransforms =
                new SQLTransformConfig.TableTransforms();
        tableTransforms.setTablePath("database-x.table-y");

        final SQLTransformConfig.TableTransforms tableTransforms2 =
                new SQLTransformConfig.TableTransforms();
        tableTransforms2.setTablePath("database-x.table-z");

        Map<String, Object> map = new HashMap<>();
        map.put("table_transform", Arrays.asList(tableTransforms, tableTransforms2));
        final ReadonlyConfig config = ReadonlyConfig.fromMap(map);
        SQLMultiCatalogTransform transform =
                new SQLMultiCatalogTransform(Collections.singletonList(DEFAULT_TABLE), config);
        TransformException exception =
                Assertions.assertThrows(
                        TransformException.class, transform::getProducedCatalogTables);
        Assertions.assertEquals(
                "ErrorCode:[TRANSFORM_COMMON-06], ErrorDescription:[The 'Sql' upstream schema not exist tables '[\"database-x.table-y\",\"database-x.table-z\"]']",
                exception.getMessage());
    }
}
