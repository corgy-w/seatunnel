/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.highgo;

import io.debezium.connector.highgo.connection.HighGoConnection;
import io.debezium.connector.highgo.connection.ReplicationMessage;
import io.debezium.pipeline.spi.OffsetContext;
import io.debezium.relational.TableId;
import io.debezium.util.Clock;

/**
 * Description: Message to clear table data
 *
 * @author czy
 * @date 2023/06/02
 */
public class TruncateRecordEmitter extends HighGoChangeRecordEmitter {
    /**
     * Constructor
     *
     * @param partition Partition
     * @param offset OffsetContext
     * @param clock Clock
     * @param connectorConfig HighGoConnectorConfig
     * @param schema HighGoSchema
     * @param connection HighGoConnection
     * @param tableId TableId
     * @param message ReplicationMessage
     */
    public TruncateRecordEmitter(
            HighGoPartition partition,
            OffsetContext offset,
            Clock clock,
            HighGoConnectorConfig connectorConfig,
            HighGoSchema schema,
            HighGoConnection connection,
            TableId tableId,
            ReplicationMessage message) {
        super(partition, offset, clock, connectorConfig, schema, connection, tableId, message);
    }
}
