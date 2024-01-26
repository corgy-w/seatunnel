package org.apache.seatunnel.transform.sql;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.event.SchemaChangeEvent;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;
import org.apache.seatunnel.transform.exception.TransformCommonError;
import org.apache.seatunnel.transform.exception.TransformExceptionUtil;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SQLMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    private final ReadonlyConfig config;

    public SQLMultiCatalogTransform(List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
        this.config = config;
    }

    @Override
    public String getPluginName() {
        return SQLTransform.PLUGIN_NAME;
    }

    @Override
    public CatalogTable getProducedCatalogTable() {
        return getProducedCatalogTables().get(0);
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return SQLTransformConfig.of(config, inputCatalogTable)
                .map(sqlConfig -> new SQLTransform(sqlConfig, config, inputCatalogTable));
    }

    public SchemaChangeEvent mapSchemaChangeEvent(SchemaChangeEvent schemaChangeEvent) {
        throw new UnsupportedOperationException("SQL Transform not support DDL");
    }

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        List<CatalogTable> outputCatalogTable = new ArrayList<>();
        preCheckForConfig(inputCatalogTables);
        for (CatalogTable table : inputCatalogTables) {
            CatalogTable newCatalogTable = table.copy();
            outputCatalogTable.add(newCatalogTable);
        }
        return outputCatalogTable;
    }

    private void preCheckForConfig(List<CatalogTable> inputCatalogTables) {
        final List<SQLTransformConfig.TableTransforms> tableTransforms =
                config.get(SQLTransformConfig.MULTI_TABLES);
        if (CollectionUtils.isEmpty(tableTransforms)) {
            return;
        }
        final List<String> fullTableNameList =
                inputCatalogTables.stream()
                        .map(table -> table.getTablePath().getFullName())
                        .collect(Collectors.toList());
        TransformExceptionUtil.withErrorCheck(
                SQLTransform.PLUGIN_NAME,
                tableTransforms.iterator(),
                entry -> {
                    final String tablePath = entry.getTablePath();
                    if (!fullTableNameList.contains(tablePath)) {
                        throw TransformCommonError.cannotFindInputTableError(
                                SQLTransform.PLUGIN_NAME, tablePath);
                    }
                });
    }
}
