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

package org.apache.seatunnel.format.cdc.custom.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Tolerate;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CustomJsonRecord implements Serializable {
    @JsonProperty("table")
    private String table;

    @JsonProperty("op_type")
    private String op;

    @JsonProperty("op_ts")
    private String opTs;

    @JsonProperty("current_ts")
    private String currentTs;

    @JsonProperty("pos")
    private String pos;

    @JsonProperty("primary_keys")
    private List<String> primaryKeys;

    @JsonProperty("before")
    private Map<String, Object> before;

    @JsonProperty("after")
    private Map<String, Object> after;

    @Tolerate
    public CustomJsonRecord() {}
}
