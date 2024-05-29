/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.pulsar.source.split;

import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.connectors.seatunnel.pulsar.source.enumerator.cursor.stop.StopCursor;
import org.apache.seatunnel.connectors.seatunnel.pulsar.source.enumerator.topic.TopicPartition;

import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.shade.com.google.common.base.Preconditions;
import org.apache.pulsar.shade.javax.annotation.Nullable;

import lombok.Getter;

import java.util.Objects;

@Getter
public class PulsarPartitionSplit implements SourceSplit {

    private final TopicPartition partition;

    private final StopCursor stopCursor;

    @Nullable private MessageId latestConsumedId;

    private final int index;
    private final int splitCount;

    public PulsarPartitionSplit(
            TopicPartition partition, StopCursor stopCursor, int index, int splitCount) {
        this(partition, stopCursor, null, index, splitCount);
    }

    public PulsarPartitionSplit(
            TopicPartition partition,
            StopCursor stopCursor,
            MessageId latestConsumedId,
            int index,
            int splitCount) {
        this.partition = Preconditions.checkNotNull(partition);
        this.stopCursor = Preconditions.checkNotNull(stopCursor);
        this.latestConsumedId = latestConsumedId;
        this.index = index;
        this.splitCount = splitCount;
    }

    public TopicPartition getPartition() {
        return partition;
    }

    public StopCursor getStopCursor() {
        return stopCursor;
    }

    @Nullable public MessageId getLatestConsumedId() {
        return latestConsumedId;
    }

    @Override
    public String splitId() {
        return partition.getFullTopicName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PulsarPartitionSplit that = (PulsarPartitionSplit) o;
        return partition.equals(that.partition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partition);
    }

    public void setLatestConsumedId(MessageId latestConsumedId) {
        this.latestConsumedId = latestConsumedId;
    }

    public PulsarPartitionSplit copy() {
        return new PulsarPartitionSplit(partition, stopCursor, latestConsumedId, index, splitCount);
    }
}
