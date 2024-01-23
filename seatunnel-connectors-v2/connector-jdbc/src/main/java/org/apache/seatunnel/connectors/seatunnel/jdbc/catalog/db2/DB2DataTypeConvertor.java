package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.db2;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.DataTypeConvertor;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.db2.DB2TypeConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter;

import org.apache.commons.collections4.MapUtils;

import com.google.auto.service.AutoService;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/** @deprecated instead by {@link DB2TypeConverter} */
@Deprecated
@AutoService(DataTypeConvertor.class)
public class DB2DataTypeConvertor implements DataTypeConvertor<String> {

    public static final String PRECISION = "precision";
    public static final String SCALE = "scale";

    public static final Long DEFAULT_PRECISION = 38L;

    public static final Integer DEFAULT_SCALE = 18;

    @Override
    public SeaTunnelDataType<?> toSeaTunnelType(String field, String connectorDataType) {
        return toSeaTunnelType(field, connectorDataType, new HashMap<>(0));
    }

    @Override
    public SeaTunnelDataType<?> toSeaTunnelType(
            String field, String connectorDataType, Map<String, Object> dataTypeProperties) {
        checkNotNull(connectorDataType, "DB2 Type cannot be null");

        Long precision = null;
        Integer scale = null;
        switch (connectorDataType) {
            case DB2TypeConverter.DB2_DECIMAL:
            case DB2TypeConverter.DB2_DEC:
            case DB2TypeConverter.DB2_NUMERIC:
            case DB2TypeConverter.DB2_NUM:
                precision = MapUtils.getLong(dataTypeProperties, PRECISION, DEFAULT_PRECISION);

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
                        .length(precision)
                        .precision(precision)
                        .scale(scale)
                        .build();

        return DB2TypeConverter.INSTANCE.convert(typeDefine).getDataType();
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
        BasicTypeDefine typeDefine = OracleTypeConverter.INSTANCE.reconvert(column);
        return typeDefine.getColumnType();
    }

    @Override
    public String getIdentity() {
        return DatabaseIdentifier.DB_2;
    }
}
