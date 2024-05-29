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

package org.apache.seatunnel.connectors.seatunnel.iotdb.source;

import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.catalog.TablePath;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class IoTDBSourceSplit implements SourceSplit {

    private static final long serialVersionUID = -1L;

    private final String splitId;

    /** final query statement */
    private final String query;

    private final int index;
    private final int splitCount;

    @Override
    public String splitId() {
        return splitId;
    }

    public String getQuery() {
        return query;
    }

    public IoTDBSourceSplit(String splitId, String query, int index, int splitCount) {
        this.splitId = splitId;
        this.query = query;
        this.index = index;
        this.splitCount = splitCount;
    }

    @Override
    public TablePath getTablePath() {
        return null;
    }
}
