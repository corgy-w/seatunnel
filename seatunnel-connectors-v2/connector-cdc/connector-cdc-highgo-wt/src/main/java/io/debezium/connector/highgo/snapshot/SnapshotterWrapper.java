/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.highgo.snapshot;

import io.debezium.connector.highgo.HighGoConnectorConfig;
import io.debezium.connector.highgo.spi.OffsetState;
import io.debezium.connector.highgo.spi.SlotState;
import io.debezium.connector.highgo.spi.Snapshotter;

/**
 * This class is a small wrapper around the snapshotter that takes care of initialization and also
 * lets us access the slotState (which we don't track currently)
 */
public class SnapshotterWrapper {

    private final Snapshotter snapshotter;
    private final SlotState slotState;

    public SnapshotterWrapper(
            Snapshotter snapshotter,
            HighGoConnectorConfig config,
            OffsetState offsetState,
            SlotState slotState) {
        this.snapshotter = snapshotter;
        this.slotState = slotState;
        this.snapshotter.init(config, offsetState, slotState);
    }

    public Snapshotter getSnapshotter() {
        return this.snapshotter;
    }

    public boolean doesSlotExist() {
        return this.slotState != null;
    }
}
