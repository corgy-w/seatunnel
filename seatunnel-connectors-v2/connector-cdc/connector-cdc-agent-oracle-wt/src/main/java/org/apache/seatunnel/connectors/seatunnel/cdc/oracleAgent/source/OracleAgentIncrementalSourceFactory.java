package org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.source;

import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.catalog.CatalogOptions;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.connector.TableSource;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSourceFactory;
import org.apache.seatunnel.api.table.factory.TableSourceFactoryContext;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.cdc.base.option.JdbcSourceOptions;
import org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.config.OracleAgentSourceOptions;
import org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.config.OracleTableConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.JdbcCatalogOptions;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import org.apache.commons.collections4.CollectionUtils;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@AutoService(Factory.class)
public class OracleAgentIncrementalSourceFactory implements TableSourceFactory {

    @Override
    public String factoryIdentifier() {
        return OracleAgentIncrementalSource.IDENTIFIER;
    }

    @Override
    public OptionRule optionRule() {
        return JdbcSourceOptions.getBaseRule()
                .required(
                        JdbcSourceOptions.USERNAME,
                        JdbcSourceOptions.PASSWORD,
                        CatalogOptions.TABLE_NAMES,
                        JdbcCatalogOptions.BASE_URL,
                        OracleAgentSourceOptions.ORACLE9BRIDGE_AGENT_HOST,
                        OracleAgentSourceOptions.ORACLE9BRIDGE_AGENT_PORT)
                .optional(
                        JdbcSourceOptions.DATABASE_NAMES,
                        JdbcSourceOptions.SERVER_TIME_ZONE,
                        JdbcSourceOptions.CONNECT_TIMEOUT_MS,
                        JdbcSourceOptions.CONNECT_MAX_RETRIES,
                        JdbcSourceOptions.CONNECTION_POOL_SIZE)
                .optional(
                        OracleAgentSourceOptions.STARTUP_MODE,
                        OracleAgentSourceOptions.STOP_MODE,
                        OracleAgentSourceOptions.TABLE_NAMES_CONFIG)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SplitT extends SourceSplit, StateT extends Serializable>
            TableSource<T, SplitT, StateT> createSource(TableSourceFactoryContext context) {
        return () -> {
            List<CatalogTable> catalogTables =
                    CatalogTableUtil.getCatalogTables(
                            DatabaseIdentifier.ORACLE,
                            context.getOptions(),
                            context.getClassLoader());
            Optional<List<OracleTableConfig>> tableConfigs =
                    context.getOptions().getOptional(OracleAgentSourceOptions.TABLE_NAMES_CONFIG);
            if (tableConfigs.isPresent()) {
                catalogTables = mergeCatalogTableConfig(catalogTables, tableConfigs.get());
            }
            SeaTunnelDataType<SeaTunnelRow> dataType =
                    CatalogTableUtil.convertToMultipleRowType(catalogTables);
            return (SeaTunnelSource<T, SplitT, StateT>)
                    new OracleAgentIncrementalSource<>(
                            context.getOptions(), dataType, catalogTables);
        };
    }

    @Override
    public Class<? extends SeaTunnelSource> getSourceClass() {
        return OracleAgentIncrementalSource.class;
    }

    private static List<CatalogTable> mergeCatalogTableConfig(
            List<CatalogTable> tables, List<OracleTableConfig> configs) {

        Map<TablePath, CatalogTable> catalogTableMap =
                tables.stream()
                        .collect(Collectors.toMap(t -> t.getTableId().toTablePath(), t -> t));
        for (OracleTableConfig catalogTableConfig : configs) {
            TablePath tablePath = TablePath.of(catalogTableConfig.getTable(), true);
            CatalogTable catalogTable = catalogTableMap.get(tablePath);
            if (CollectionUtils.isNotEmpty(catalogTableConfig.getPrimaryKeys())) {
                List<String> columnNames =
                        catalogTable.getTableSchema().getColumns().stream()
                                .map(c -> c.getName())
                                .collect(Collectors.toList());
                for (String pk : catalogTableConfig.getPrimaryKeys()) {
                    if (!columnNames.contains(pk)) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "Primary key(%s) is not in table(%s) columns(%s)",
                                        pk, tablePath, columnNames));
                    }
                }
                catalogTable =
                        CatalogTable.of(
                                catalogTable.getTableId(),
                                TableSchema.builder()
                                        .columns(catalogTable.getTableSchema().getColumns())
                                        .constraintKey(
                                                catalogTable.getTableSchema().getConstraintKeys())
                                        .primaryKey(
                                                PrimaryKey.of(
                                                        "pk"
                                                                + Math.abs(
                                                                        catalogTableConfig
                                                                                .getPrimaryKeys()
                                                                                .hashCode()),
                                                        catalogTableConfig.getPrimaryKeys()))
                                        .build(),
                                catalogTable.getOptions(),
                                catalogTable.getPartitionKeys(),
                                catalogTable.getComment());
                log.info(
                        "Override primary key({}) for catalog table {}",
                        catalogTableConfig.getPrimaryKeys(),
                        tablePath);
            }
            catalogTableMap.put(tablePath, catalogTable);
        }
        return catalogTableMap.values().stream().collect(Collectors.toList());
    }
}
