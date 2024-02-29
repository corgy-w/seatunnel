package org.apache.seatunnel.connectors.dws.guassdb.sink.exception;

import org.apache.seatunnel.common.exception.SeaTunnelErrorCode;
import org.apache.seatunnel.common.exception.SeaTunnelRuntimeException;

public class DwsGaussDBConnectorException extends SeaTunnelRuntimeException {

    public DwsGaussDBConnectorException(
            SeaTunnelErrorCode seaTunnelErrorCode, String errorMessage) {
        super(seaTunnelErrorCode, errorMessage);
    }

    public DwsGaussDBConnectorException(
            SeaTunnelErrorCode seaTunnelErrorCode, String errorMessage, Throwable cause) {
        super(seaTunnelErrorCode, errorMessage, cause);
    }

    public DwsGaussDBConnectorException(SeaTunnelErrorCode seaTunnelErrorCode, Throwable cause) {
        super(seaTunnelErrorCode, cause);
    }
}
