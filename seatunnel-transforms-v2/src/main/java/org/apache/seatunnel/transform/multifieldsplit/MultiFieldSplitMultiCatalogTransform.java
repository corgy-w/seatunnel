package org.apache.seatunnel.transform.multifieldsplit;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;

import java.util.List;
import java.util.Optional;

public class MultiFieldSplitMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public MultiFieldSplitMultiCatalogTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    public String getPluginName() {
        return MultiFieldSplitTransform.PLUGIN_NAME;
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return MultiFieldSplitTransformConfig.of(config, inputCatalogTable)
                .map(
                        multiFieldSplitTransformConfig ->
                                new MultiFieldSplitTransform(
                                        multiFieldSplitTransformConfig, inputCatalogTable));
    }
}
