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

package org.apache.seatunnel.connectors.seatunnel.pi.split;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PI split strategy for creating intelligent splits with support for dynamic split size adjustment
 */
public class PISplitStrategy {

    private static final Logger log = LoggerFactory.getLogger(PISplitStrategy.class);

    private static final int DEFAULT_WEBIDS_PER_SPLIT = 150;
    private static final int MAX_URL_LENGTH = 1800; // Reserve safety margin

    /**
     * Create split list
     *
     * @param webIds WebID list
     * @param startTime Start time
     * @param endTime End time
     * @param maxWebIdsPerSplit Maximum number of WebIDs per split
     * @return Split list
     */
    public List<PISplit> createSplits(
            List<String> webIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int maxWebIdsPerSplit) {

        if (webIds == null || webIds.isEmpty()) {
            log.warn("WebID list is empty, cannot create splits");
            return new ArrayList<>();
        }

        if (maxWebIdsPerSplit <= 0) {
            maxWebIdsPerSplit = DEFAULT_WEBIDS_PER_SPLIT;
        }

        log.info(
                "Starting to create splits, total WebIDs: {}, max WebIDs per split: {}",
                webIds.size(),
                maxWebIdsPerSplit);

        List<PISplit> splits = new ArrayList<>();

        // Split WebID list by split size
        /**
         * Method approach: 1. Split webIds into multiple sublists based on maxWebIdsPerSplit. In
         * this case, if the total number of webids is only 100, then it will only be split into one
         * sublist containing all 100 webids. 2. Estimate URL length for each sublist 3. If URL
         * length exceeds limit, halve maxWebIdsPerSplit and recursively call createSplits method 4.
         * If URL length is within limit, create PISplit object and add to splits list
         */
        for (int i = 0; i < webIds.size(); i += maxWebIdsPerSplit) {
            int endIndex = Math.min(i + maxWebIdsPerSplit, webIds.size());
            List<String> splitWebIds = webIds.subList(i, endIndex);

            // Validate URL length
            if (estimateUrlLength(splitWebIds) > MAX_URL_LENGTH) {
                log.warn(
                        "Split {} URL length exceeds limit, performing subdivision",
                        i / maxWebIdsPerSplit);
                // Recursive subdivision
                splits.addAll(createSplits(splitWebIds, startTime, endTime, maxWebIdsPerSplit / 2));
            } else {
                String splitId = "split-" + (i / maxWebIdsPerSplit);
                PISplit split =
                        new PISplit(splitId, new ArrayList<>(splitWebIds), startTime, endTime);
                splits.add(split);

                log.info("Created split: {}, WebID count: {}", splitId, splitWebIds.size());
            }
        }

        log.info("Split creation completed, total splits: {}", splits.size());
        return splits;
    }

    /** Estimate URL length for determining whether split subdivision is needed */
    private int estimateUrlLength(List<String> webIds) {
        if (webIds == null || webIds.isEmpty()) {
            return 0;
        }

        // Base URL length estimation
        int baseUrlLength = 100; // Base URL part
        int paramLength = 0;

        for (String webId : webIds) {
            // webid=xxx&
            paramLength += 6 + (webId != null ? webId.length() : 0) + 1;
        }

        // Add time parameter length estimation
        int timeParamLength = 100; // startTime and endTime parameters

        return baseUrlLength + paramLength + timeParamLength;
    }
}
