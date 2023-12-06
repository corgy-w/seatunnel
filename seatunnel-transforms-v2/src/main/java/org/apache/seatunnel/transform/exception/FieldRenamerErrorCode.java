package org.apache.seatunnel.transform.exception;

import org.apache.seatunnel.common.exception.SeaTunnelErrorCode;

public enum FieldRenamerErrorCode implements SeaTunnelErrorCode {
    DUPLICATE_NAME(
            "FIELD_RENAMER-01",
            "The FieldRenamer renamed target field name had duplicate name: '<tableWithDuplicateName>'");

    private final String code;
    private final String description;

    FieldRenamerErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getDescription() {
        return this.description;
    }
}
