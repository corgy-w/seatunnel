package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.kingbase;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.utils.CatalogUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.kingbase.KingbaseTypeConverter;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class KingbaseCreateTableSqlBuilder {
    private List<Column> columns;
    private PrimaryKey primaryKey;
    private String sourceCatalogName;

    public KingbaseCreateTableSqlBuilder(CatalogTable catalogTable) {
        this.columns = catalogTable.getTableSchema().getColumns();
        this.primaryKey = catalogTable.getTableSchema().getPrimaryKey();
        this.sourceCatalogName = catalogTable.getCatalogName();
    }

    public String build(TablePath tablePath) {
        return build(tablePath, "");
    }

    public String build(TablePath tablePath, String fieldIde) {
        StringBuilder createTableSql = new StringBuilder();
        createTableSql
                .append(CatalogUtils.quoteIdentifier("CREATE TABLE IF NOT EXISTS ", fieldIde))
                .append(tablePath.getSchemaAndTableName("\""))
                .append(" (\n");

        List<String> columnSqls =
                columns.stream()
                        .map(
                                column ->
                                        CatalogUtils.quoteIdentifier(
                                                buildColumnSql(column), fieldIde))
                        .collect(Collectors.toList());

        createTableSql.append(String.join(",\n", columnSqls));
        createTableSql.append("\n);");

        List<String> commentSqls =
                columns.stream()
                        .filter(column -> StringUtils.isNotBlank(column.getComment()))
                        .map(
                                columns ->
                                        buildColumnCommentSql(
                                                columns,
                                                tablePath.getSchemaAndTableName("\""),
                                                fieldIde))
                        .collect(Collectors.toList());

        if (!commentSqls.isEmpty()) {
            createTableSql.append("\n");
            createTableSql.append(String.join(";\n", commentSqls)).append(";");
        }

        return createTableSql.toString();
    }

    private String buildColumnSql(Column column) {
        StringBuilder columnSql = new StringBuilder();
        columnSql.append("\"").append(column.getName()).append("\" ");

        // For simplicity, assume the column type in SeaTunnelDataType is the same as in PostgreSQL
        String columnType =
                (sourceCatalogName.equals(DatabaseIdentifier.KINGBASE)
                                        || sourceCatalogName.equals(DatabaseIdentifier.POSTGRESQL))
                                && StringUtils.isNotBlank(column.getSourceType())
                        ? column.getSourceType()
                        : KingbaseTypeConverter.INSTANCE.reconvert(column).getColumnType();
        columnSql.append(columnType);

        // Add NOT NULL if column is not nullable
        if (!column.isNullable()) {
            columnSql.append(" NOT NULL");
        }

        // Add primary key directly after the column if it is a primary key
        if (primaryKey != null && primaryKey.getColumnNames().contains(column.getName())) {
            columnSql.append(" PRIMARY KEY");
        }

        // Add default value if exists
        //        if (column.getDefaultValue() != null) {
        //            columnSql.append(" DEFAULT
        // '").append(column.getDefaultValue().toString()).append("'");
        //        }

        return columnSql.toString();
    }

    private String buildColumnCommentSql(Column column, String tableName, String fieldIde) {
        StringBuilder columnCommentSql = new StringBuilder();
        columnCommentSql
                .append(CatalogUtils.quoteIdentifier("COMMENT ON COLUMN ", fieldIde))
                .append(tableName)
                .append(".");
        columnCommentSql
                .append(CatalogUtils.quoteIdentifier(column.getName(), fieldIde, "\""))
                .append(CatalogUtils.quoteIdentifier(" IS '", fieldIde))
                .append(column.getComment())
                .append("'");
        return columnCommentSql.toString();
    }
}
