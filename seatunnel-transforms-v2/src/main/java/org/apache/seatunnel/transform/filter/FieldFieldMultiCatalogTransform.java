package org.apache.seatunnel.transform.filter;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;

import java.util.List;
import java.util.Optional;

public class FieldFieldMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public FieldFieldMultiCatalogTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    public String getPluginName() {
        return FilterFieldTransform.PLUGIN_NAME;
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return FilterFieldTransformConfig.of(config, inputCatalogTable)
                .map(
                        filterFieldTransformConfig ->
                                new FilterFieldTransform(
                                        filterFieldTransformConfig, inputCatalogTable));
    }
}
