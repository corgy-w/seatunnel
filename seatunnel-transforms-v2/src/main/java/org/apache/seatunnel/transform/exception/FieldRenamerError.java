package org.apache.seatunnel.transform.exception;

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.apache.seatunnel.transform.exception.FieldRenamerErrorCode.DUPLICATE_NAME;

public class FieldRenamerError {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static TransformException tableDuplicateFieldNameError(
            Map<String, Map<String, String>> tableWithDuplicateName) {
        Map<String, String> params = new HashMap<>();
        try {
            params.put(
                    "tableWithDuplicateName",
                    OBJECT_MAPPER.writeValueAsString(tableWithDuplicateName));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new TransformException(DUPLICATE_NAME, params);
    }
}
