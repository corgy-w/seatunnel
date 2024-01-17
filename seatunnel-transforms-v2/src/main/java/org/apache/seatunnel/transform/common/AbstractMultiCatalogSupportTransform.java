package org.apache.seatunnel.transform.common;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import org.apache.seatunnel.api.common.PrepareFailException;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractMultiCatalogSupportTransform
        implements SeaTunnelTransform<SeaTunnelRow> {

    protected List<CatalogTable> inputCatalogTables;

    protected List<CatalogTable> outputCatalogTables;

    protected Map<String, SeaTunnelTransform<SeaTunnelRow>> transformMap;

    public AbstractMultiCatalogSupportTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        this.inputCatalogTables = inputCatalogTables;
        this.transformMap = new HashMap<>();
        inputCatalogTables.forEach(
                inputCatalogTable -> {
                    String tableId = inputCatalogTable.getTableId().toTablePath().toString();
                    transformMap.put(
                            tableId,
                            buildTransform(inputCatalogTable, config)
                                    .orElse(new IdentityTransform(inputCatalogTable)));
                });

        this.outputCatalogTables =
                inputCatalogTables.stream()
                        .map(
                                inputCatalogTable -> {
                                    String tableName =
                                            inputCatalogTable.getTableId().toTablePath().toString();
                                    return transformMap.get(tableName).getProducedCatalogTable();
                                })
                        .collect(Collectors.toList());
    }

    @Override
    public SeaTunnelRow map(SeaTunnelRow row) {
        if (transformMap.size() == 1) {
            return transformMap.values().iterator().next().map(row);
        }
        return transformMap.get(row.getTableId()).map(row);
    }

    protected abstract Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config);

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        return outputCatalogTables;
    }

    @Override
    public CatalogTable getProducedCatalogTable() {
        return outputCatalogTables.get(0);
    }

    @Override
    public SeaTunnelDataType<SeaTunnelRow> getProducedType() {
        return outputCatalogTables.get(0).getTableSchema().toPhysicalRowDataType();
    }

    @Override
    public void setTypeInfo(SeaTunnelDataType<SeaTunnelRow> inputDataType) {}

    @Override
    public void prepare(Config pluginConfig) throws PrepareFailException {}

    public static class IdentityTransform implements SeaTunnelTransform<SeaTunnelRow> {
        private CatalogTable catalogTable;

        @Override
        public String getPluginName() {
            return "Identity";
        }

        public IdentityTransform(CatalogTable catalogTable) {
            this.catalogTable = catalogTable;
        }

        @Override
        public SeaTunnelRow map(SeaTunnelRow row) {
            return row;
        }

        @Override
        public List<CatalogTable> getProducedCatalogTables() {
            return Collections.singletonList(catalogTable);
        }

        @Override
        public CatalogTable getProducedCatalogTable() {
            return catalogTable;
        }

        @Override
        public SeaTunnelDataType<SeaTunnelRow> getProducedType() {
            return catalogTable.getTableSchema().toPhysicalRowDataType();
        }

        @Override
        public void setTypeInfo(SeaTunnelDataType<SeaTunnelRow> inputDataType) {}

        @Override
        public void prepare(Config pluginConfig) throws PrepareFailException {}
    }
}
