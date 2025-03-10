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

package io.debezium.connector.dameng;

import io.debezium.connector.base.ChangeEventQueueMetrics;
import io.debezium.connector.common.CdcSourceTaskContext;
import io.debezium.pipeline.metrics.DefaultChangeEventSourceMetricsFactory;
import io.debezium.pipeline.metrics.StreamingChangeEventSourceMetrics;
import io.debezium.pipeline.source.spi.EventMetadataProvider;

public class DamengChangeEventSourceMetricsFactory
        extends DefaultChangeEventSourceMetricsFactory<DamengPartition> {

    private final DamengStreamingChangeEventSourceMetrics streamingMetrics;

    public DamengChangeEventSourceMetricsFactory(
            DamengStreamingChangeEventSourceMetrics streamingMetrics) {
        this.streamingMetrics = streamingMetrics;
    }

    @Override
    public <T extends CdcSourceTaskContext>
            StreamingChangeEventSourceMetrics<DamengPartition> getStreamingMetrics(
                    T taskContext,
                    ChangeEventQueueMetrics changeEventQueueMetrics,
                    EventMetadataProvider eventMetadataProvider) {
        return streamingMetrics;
    }
}
