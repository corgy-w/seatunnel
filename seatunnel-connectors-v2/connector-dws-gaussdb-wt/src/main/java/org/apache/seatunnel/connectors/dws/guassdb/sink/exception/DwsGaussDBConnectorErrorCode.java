package org.apache.seatunnel.connectors.dws.guassdb.sink.exception;

import org.apache.seatunnel.common.exception.SeaTunnelErrorCode;

public enum DwsGaussDBConnectorErrorCode implements SeaTunnelErrorCode {
    FLUSH_DATA_ERROR("DWS_GAUSSDB-001", "Flush data error");

    private String code;
    private String description;

    DwsGaussDBConnectorErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
