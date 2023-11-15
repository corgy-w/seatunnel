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

package org.apache.seatunnel.transform.dmleventfilter;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.transform.filterrowkind.FilterRowKinkTransformConfig;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class DMLEventFilterTransformConfig implements Serializable {

    public static final Option<List<RowKind>> INCLUDE_KINDS =
            Options.key("include_kinds")
                    .listType(RowKind.class)
                    .noDefaultValue()
                    .withDescription("the row kinds to include");

    public static final Option<List<RowKind>> EXCLUDE_KINDS =
            Options.key("exclude_kinds")
                    .listType(RowKind.class)
                    .noDefaultValue()
                    .withDescription("the row kinds to exclude");

    public static final OptionRule OPTION_RULE =
            OptionRule.builder()
                    .exclusive(
                            FilterRowKinkTransformConfig.EXCLUDE_KINDS,
                            FilterRowKinkTransformConfig.INCLUDE_KINDS)
                    .build();

    private Set<RowKind> includeKinds = Collections.emptySet();
    private Set<RowKind> excludeKinds = Collections.emptySet();

    public static DMLEventFilterTransformConfig of(ReadonlyConfig config) {
        DMLEventFilterTransformConfig filterRowKinkTransformConfig =
                new DMLEventFilterTransformConfig();
        if (config.get(INCLUDE_KINDS) != null) {
            filterRowKinkTransformConfig.setIncludeKinds(new HashSet<>(config.get(INCLUDE_KINDS)));
        }
        if (config.get(EXCLUDE_KINDS) != null) {
            filterRowKinkTransformConfig.setExcludeKinds(new HashSet<>(config.get(EXCLUDE_KINDS)));
        }
        return filterRowKinkTransformConfig;
    }
}
