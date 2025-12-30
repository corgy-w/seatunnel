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

package org.apache.seatunnel.api.transform;

import java.util.List;

/**
 * SeaTunnelFlatMapTransform is a transform that implements one-to-many row transformation.
 *
 * <p>This interface is used for transforms that take a single input row and produce zero, one, or
 * multiple output rows.
 *
 * <p>Examples include:
 *
 * <ul>
 *   <li>Exploding arrays or collections into multiple rows
 *   <li>Splitting CDC UPDATE events into separate BEFORE and AFTER rows
 *   <li>Filtering rows (returning empty list)
 *   <li>Duplicating rows with different values
 * </ul>
 *
 * @param <T> The type of the data being transformed
 */
public interface SeaTunnelFlatMapTransform<T> extends SeaTunnelTransform<T> {

    /**
     * Transform input data to {@link this#getProducedCatalogTable().getSeaTunnelRowType()} types
     * data.
     *
     * @param row the data need be transformed.
     * @return list of transformed data (can be empty, single, or multiple records). Empty list
     *     means filter this row. Null values in the list will be filtered out.
     */
    List<T> flatMap(T row);
}
