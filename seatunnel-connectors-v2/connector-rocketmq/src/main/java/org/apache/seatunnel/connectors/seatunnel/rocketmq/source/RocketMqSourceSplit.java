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

package org.apache.seatunnel.connectors.seatunnel.rocketmq.source;

import org.apache.seatunnel.api.source.SourceSplit;

import org.apache.rocketmq.common.message.MessageQueue;

import lombok.Getter;
import lombok.Setter;

/** define rocketmq source split */
@Getter
@Setter
public class RocketMqSourceSplit implements SourceSplit {
    private MessageQueue messageQueue;
    private long startOffset = -1L;
    private long endOffset = -1L;

    private final int index;
    private int splitCount;

    public RocketMqSourceSplit(
            MessageQueue messageQueue,
            long startOffset,
            long endOffset,
            int index,
            int splitCount) {
        this.messageQueue = messageQueue;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.index = index;
        this.splitCount = splitCount;
    }

    @Override
    public String splitId() {
        return this.messageQueue.getTopic()
                + "-"
                + this.messageQueue.getBrokerName()
                + "-"
                + this.messageQueue.getQueueId();
    }

    public RocketMqSourceSplit copy() {
        return new RocketMqSourceSplit(
                this.messageQueue, this.getStartOffset(), this.getEndOffset(), index, splitCount);
    }
}
