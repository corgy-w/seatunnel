/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.highgo.connection;

import io.debezium.connector.highgo.HighGoConnectorConfig;
import io.debezium.connector.highgo.HighGoSchema;

/**
 * Contextual data required by {@link MessageDecoder}s.
 *
 * @author Chris Cranford
 */
public class MessageDecoderContext {

    private final HighGoConnectorConfig config;
    private final HighGoSchema schema;

    public MessageDecoderContext(HighGoConnectorConfig config, HighGoSchema schema) {
        this.config = config;
        this.schema = schema;
    }

    public HighGoConnectorConfig getConfig() {
        return config;
    }

    public HighGoSchema getSchema() {
        return schema;
    }
}
