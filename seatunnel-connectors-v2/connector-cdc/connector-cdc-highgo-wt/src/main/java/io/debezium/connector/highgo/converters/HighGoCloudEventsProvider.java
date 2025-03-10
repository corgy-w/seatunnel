/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.highgo.converters;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;

import io.debezium.connector.highgo.Module;
import io.debezium.connector.highgo.utils.CloudEventsProvider;
import io.debezium.converters.spi.CloudEventsMaker;
import io.debezium.converters.spi.RecordParser;
import io.debezium.converters.spi.SerializerType;

/**
 * An implementation of {@link CloudEventsProvider} for HighGo.
 *
 * @author Chris Cranford
 */
public class HighGoCloudEventsProvider implements CloudEventsProvider {
    @Override
    public String getName() {
        return Module.name();
    }

    @Override
    public RecordParser createParser(Schema schema, Struct record) {
        return new HighGoRecordParser(schema, record);
    }

    @Override
    public CloudEventsMaker createMaker(
            RecordParser parser, SerializerType contentType, String dataSchemaUriBase) {
        return null;
    }
}
