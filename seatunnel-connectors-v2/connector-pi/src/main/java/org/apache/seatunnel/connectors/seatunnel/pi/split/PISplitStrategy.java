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
    private static final int MAX_URL_LENGTH = 1800;
    private static final int MIN_WEBIDS_PER_SPLIT =
            1; // Minimum split size to prevent infinite recursion

    /**
     * Create split list with recursion termination protection this is a recursive method
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
                if (splitWebIds.size() == 1) {
                    String webId = splitWebIds.get(0);
                    int urlLength = estimateUrlLength(splitWebIds);
                    throw new IllegalArgumentException(
                            String.format(
                                    "Single WebID URL length exceeds limit (%d > %d). WebID: %s. "
                                            + "Consider: 1) Use shorter WebID paths, 2) Increase MAX_URL_LENGTH.",
                                    urlLength,
                                    MAX_URL_LENGTH,
                                    webId.length() > 100
                                            ? webId.substring(0, 100) + "..."
                                            : webId));
                }

                // Apply step size lower bound protection
                int nextStepSize = Math.max(MIN_WEBIDS_PER_SPLIT, maxWebIdsPerSplit / 2);
                log.warn(
                        "Split {} URL length exceeds limit (estimated: {} chars), performing subdivision from {} to {} WebIDs per split",
                        i / maxWebIdsPerSplit,
                        estimateUrlLength(splitWebIds),
                        maxWebIdsPerSplit,
                        nextStepSize);

                // Recursive subdivision with protection
                splits.addAll(createSplits(splitWebIds, startTime, endTime, nextStepSize));
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

    /**
     * Estimate URL length for determining whether split subdivision is needed More accurate
     * estimation based on actual PI Web API URL structure
     */
    private int estimateUrlLength(List<String> webIds) {
        if (webIds == null || webIds.isEmpty()) {
            return 0;
        }

        // Base URL length estimation (more accurate)
        // Example: https://server:8443/piwebapi/streamsets/recorded?
        int baseUrlLength = 150;

        // WebID parameters: webid=P1AbEiO7ub6ZrVQ0-uLVXfPJQVQAAAAUE1EQVRBSE9TVFxDREE158&
        int webIdParamLength = 0;
        for (String webId : webIds) {
            if (webId != null) {
                // "webid=" + webId + "&"
                webIdParamLength += 6 + webId.length() + 1;
            }
        }

        // Time parameters: startTime=2024-01-01T00:00:00Z&endTime=2024-01-01T01:00:00Z&
        int timeParamLength = 120;

        // Additional parameters: maxCount=1000&boundaryType=Inside&
        int additionalParamLength = 50;

        int totalLength =
                baseUrlLength + webIdParamLength + timeParamLength + additionalParamLength;

        log.debug("Estimated URL length: {} chars for {} WebIDs", totalLength, webIds.size());
        return totalLength;
    }
}
