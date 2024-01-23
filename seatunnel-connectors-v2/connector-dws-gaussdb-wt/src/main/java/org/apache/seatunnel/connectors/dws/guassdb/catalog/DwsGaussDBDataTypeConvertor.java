package org.apache.seatunnel.connectors.dws.guassdb.catalog;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.DataTypeConvertor;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.connectors.dws.guassdb.config.DwsGaussDBConfig;

import org.apache.commons.collections4.MapUtils;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The DwsGaussDB data type is same with PG so this class is copied from {@link
 * org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.psql.PostgresDataTypeConvertor}.
 */
@Deprecated
@Slf4j
@AutoService(DwsGaussDBDataTypeConvertor.class)
public class DwsGaussDBDataTypeConvertor implements DataTypeConvertor<String>, Serializable {
    public static final String PRECISION = "precision";
    public static final String SCALE = "scale";
    public static final Integer DEFAULT_PRECISION = 38;
    public static final Integer DEFAULT_SCALE = 18;

    @Override
    public SeaTunnelDataType<?> toSeaTunnelType(String field, String connectorDataType) {
        return toSeaTunnelType(field, connectorDataType, new HashMap<>(0));
    }

    @Override
    public SeaTunnelDataType<?> toSeaTunnelType(
            String field, String connectorDataType, Map<String, Object> dataTypeProperties) {
        checkNotNull(connectorDataType, "Postgres Type cannot be null");

        Integer precision = null;
        Integer scale = null;
        switch (connectorDataType) {
            case DwsGaussDBTypeConverter.PG_NUMERIC:
                precision = MapUtils.getInteger(dataTypeProperties, PRECISION, DEFAULT_PRECISION);

                scale = MapUtils.getInteger(dataTypeProperties, SCALE, DEFAULT_SCALE);
                break;
            default:
                break;
        }

        BasicTypeDefine typeDefine =
                BasicTypeDefine.builder()
                        .name(field)
                        .columnType(connectorDataType)
                        .dataType(connectorDataType)
                        .length(precision == null ? null : Long.valueOf(precision))
                        .precision(precision == null ? null : Long.valueOf(precision))
                        .scale(scale)
                        .build();
        return DwsGaussDBTypeConverter.INSTANCE.convert(typeDefine).getDataType();
    }

    @Override
    public String toConnectorType(
            String field,
            SeaTunnelDataType<?> seaTunnelDataType,
            Map<String, Object> dataTypeProperties) {
        checkNotNull(seaTunnelDataType, "seaTunnelDataType cannot be null");

        Long precision = MapUtils.getLong(dataTypeProperties, PRECISION);
        Integer scale = MapUtils.getInteger(dataTypeProperties, SCALE);
        Column column =
                PhysicalColumn.builder()
                        .name(field)
                        .dataType(seaTunnelDataType)
                        .columnLength(precision)
                        .scale(scale)
                        .nullable(true)
                        .build();
        BasicTypeDefine typeDefine = DwsGaussDBTypeConverter.INSTANCE.reconvert(column);
        return typeDefine.getColumnType();
    }

    @Override
    public String getIdentity() {
        return DwsGaussDBConfig.CONNECTOR_NAME;
    }
}
