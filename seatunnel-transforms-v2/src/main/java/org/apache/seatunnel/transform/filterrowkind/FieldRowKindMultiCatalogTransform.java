package org.apache.seatunnel.transform.filterrowkind;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;

import java.util.List;
import java.util.Optional;

public class FieldRowKindMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public FieldRowKindMultiCatalogTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    public String getPluginName() {
        return FilterRowKindTransform.PLUGIN_NAME;
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return FilterRowKinkTransformConfig.of(config, inputCatalogTable)
                .map(
                        filterRowKindTransformConfig ->
                                new FilterRowKindTransform(
                                        filterRowKindTransformConfig, inputCatalogTable));
    }
}
