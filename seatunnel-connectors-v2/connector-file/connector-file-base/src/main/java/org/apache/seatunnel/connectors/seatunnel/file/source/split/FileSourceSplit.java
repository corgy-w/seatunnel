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

package org.apache.seatunnel.connectors.seatunnel.file.source.split;

import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.catalog.TablePath;

import lombok.Getter;

@Getter
public class FileSourceSplit implements SourceSplit {
    private static final long serialVersionUID = 1L;

    private final String tableId;
    private final String filePath;

    private final int index;
    private final int splitCount;

    public FileSourceSplit(String splitId, int index, int splitCount) {
        this.filePath = splitId;
        this.tableId = null;
        this.index = index;
        this.splitCount = splitCount;
    }

    public FileSourceSplit(String tableId, String filePath, int index, int splitCount) {
        this.tableId = tableId;
        this.filePath = filePath;
        this.index = index;
        this.splitCount = splitCount;
    }

    @Override
    public TablePath getTablePath() {
        return tableId != null ? TablePath.of(tableId) : null;
    }

    @Override
    public String splitId() {
        // In order to be compatible with the split before the upgrade, when tableId is null,
        // filePath is directly returned
        if (tableId == null) {
            return filePath;
        }
        return tableId + "_" + filePath;
    }
}
