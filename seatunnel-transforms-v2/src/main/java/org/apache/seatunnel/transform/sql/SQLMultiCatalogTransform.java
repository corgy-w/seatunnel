package org.apache.seatunnel.transform.sql;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.event.SchemaChangeEvent;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.AbstractMultiCatalogSupportTransform;

import java.util.List;
import java.util.Optional;

public class SQLMultiCatalogTransform extends AbstractMultiCatalogSupportTransform {

    public SQLMultiCatalogTransform(List<CatalogTable> inputCatalogTables, ReadonlyConfig config) {
        super(inputCatalogTables, config);
    }

    @Override
    public String getPluginName() {
        return SQLTransform.PLUGIN_NAME;
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
}
