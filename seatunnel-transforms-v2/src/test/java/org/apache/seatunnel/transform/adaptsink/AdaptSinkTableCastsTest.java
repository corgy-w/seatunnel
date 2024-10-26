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

package org.apache.seatunnel.transform.adaptsink;

import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.type.BasicType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AdaptSinkTableCastsTest {

    @Test
    public void testCastStringToTimestamp() {
        LocalDate date =
                AdaptSinkTableCasts.castAsDate(
                        PhysicalColumn.builder().name("f1").dataType(BasicType.STRING_TYPE).build(),
                        "20240901");
        Assertions.assertEquals(LocalDate.of(2024, 9, 1), date);

        LocalDateTime dateTime =
                AdaptSinkTableCasts.castAsTimestamp(
                        PhysicalColumn.builder().name("f1").dataType(BasicType.STRING_TYPE).build(),
                        "20240901 01:10:10");
        Assertions.assertEquals(LocalDateTime.of(2024, 9, 1, 1, 10, 10), dateTime);
    }
}
