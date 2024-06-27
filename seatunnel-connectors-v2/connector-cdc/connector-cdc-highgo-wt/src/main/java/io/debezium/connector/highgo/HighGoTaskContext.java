/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.highgo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.annotation.ThreadSafe;
import io.debezium.connector.common.CdcSourceTaskContext;
import io.debezium.connector.highgo.connection.HighGoConnection;
import io.debezium.connector.highgo.connection.ReplicationConnection;
import io.debezium.connector.highgo.spi.SlotState;
import io.debezium.relational.TableId;
import io.debezium.schema.TopicSelector;
import io.debezium.util.Clock;
import io.debezium.util.ElapsedTimeStrategy;

import java.sql.SQLException;
import java.util.Collections;

/**
 * The context of a {@link}. This deals with most of the brunt of reading various configuration
 * options and creating other objects with these various options.
 *
 * @author Horia Chiorean (hchiorea@redhat.com)
 */
@ThreadSafe
public class HighGoTaskContext extends CdcSourceTaskContext {

    protected static final Logger LOGGER = LoggerFactory.getLogger(HighGoTaskContext.class);

    private final HighGoConnectorConfig config;
    private final TopicSelector<TableId> topicSelector;
    private final HighGoSchema schema;

    private ElapsedTimeStrategy refreshXmin;
    private Long lastXmin;

    public HighGoTaskContext(
            HighGoConnectorConfig config,
            HighGoSchema schema,
            TopicSelector<TableId> topicSelector) {
        super(config.getContextName(), config.getLogicalName(), Collections::emptySet);

        this.config = config;
        if (config.xminFetchInterval().toMillis() > 0) {
            this.refreshXmin =
                    ElapsedTimeStrategy.constant(
                            Clock.SYSTEM, config.xminFetchInterval().toMillis());
        }
        this.topicSelector = topicSelector;
        assert schema != null;
        this.schema = schema;
    }

    protected TopicSelector<TableId> topicSelector() {
        return topicSelector;
    }

    protected HighGoSchema schema() {
        return schema;
    }

    protected HighGoConnectorConfig config() {
        return config;
    }

    public void refreshSchema(HighGoConnection connection, boolean printReplicaIdentityInfo)
            throws SQLException {
        schema.refresh(connection, printReplicaIdentityInfo);
    }

    Long getSlotXmin(HighGoConnection connection) throws SQLException {
        // when xmin fetch is set to 0, we don't track it to ignore any performance of querying the
        // slot periodically
        if (config.xminFetchInterval().toMillis() <= 0) {
            return null;
        }
        assert (this.refreshXmin != null);

        if (this.refreshXmin.hasElapsed()) {
            lastXmin = getCurrentSlotState(connection).slotCatalogXmin();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Fetched new xmin from slot of {}", lastXmin);
            }
        } else {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("reusing xmin value of {}", lastXmin);
            }
        }

        return lastXmin;
    }

    private SlotState getCurrentSlotState(HighGoConnection connection) throws SQLException {
        return connection.getReplicationSlotState(
                config.slotName(), config.plugin().getPostgresPluginName());
    }

    public ReplicationConnection createReplicationConnection(boolean doSnapshot)
            throws SQLException {
        final boolean dropSlotOnStop = config.dropSlotOnStop();
        if (dropSlotOnStop) {
            LOGGER.warn(
                    "Connector has enabled automated replication slot removal upon restart ({} = true). "
                            + "This setting is not recommended for production environments, as a new replication slot "
                            + "will be created after a connector restart, resulting in missed data change events.",
                    HighGoConnectorConfig.DROP_SLOT_ON_STOP.name());
        }
        return ReplicationConnection.builder(config)
                .withSlot(config.slotName())
                .withPublication(config.publicationName())
                .withTableFilter(config.getTableFilters())
                .withPublicationAutocreateMode(config.publicationAutocreateMode())
                .withPlugin(config.plugin())
                .dropSlotOnClose(dropSlotOnStop)
                .streamParams(config.streamParams())
                .statusUpdateInterval(config.statusUpdateInterval())
                .withTypeRegistry(schema.getTypeRegistry())
                .doSnapshot(doSnapshot)
                .withSchema(schema)
                .build();
    }

    HighGoConnectorConfig getConfig() {
        return config;
    }
}
