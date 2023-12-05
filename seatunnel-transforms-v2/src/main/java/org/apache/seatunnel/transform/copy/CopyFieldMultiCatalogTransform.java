package org.apache.seatunnel.transform.copy;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;

import java.util.List;
import java.util.Optional;

public class CopyFieldMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public CopyFieldMultiCatalogTransform(
            List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    public String getPluginName() {
        return CopyFieldTransform.PLUGIN_NAME;
    }

    @Override
    protected Optional<SeaTunnelTransform<SeaTunnelRow>> buildTransform(
            CatalogTable inputCatalogTable, ReadonlyConfig config) {
        return CopyTransformConfig.of(config, inputCatalogTable)
                .map(copyConfig -> new CopyFieldTransform(copyConfig, inputCatalogTable));
    }
}
