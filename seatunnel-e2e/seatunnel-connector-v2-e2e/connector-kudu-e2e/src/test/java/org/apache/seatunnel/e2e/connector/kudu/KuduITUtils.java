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

package org.apache.seatunnel.e2e.connector.kudu;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class KuduITUtils {
    public static String getLocalTimeStr(String str) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        LocalDateTime localDateTime = LocalDateTime.parse(str, formatter);

        ZonedDateTime zonedDateTime = localDateTime.atZone(java.time.ZoneId.systemDefault());

        DateTimeFormatter outFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");

        return zonedDateTime.format(outFormatter);
    }

    public static void main(String[] args) {
        String localTimeStr = KuduITUtils.getLocalTimeStr("2020-02-02T02:02:02");
        System.out.println(localTimeStr);
    }
}
