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

package org.apache.seatunnel.transform.fieldreplacer;

import org.apache.seatunnel.shade.com.fasterxml.jackson.annotation.JsonAlias;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;

import org.apache.logging.log4j.util.Strings;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

@Getter
@Setter
public class FieldReplacerTransformConfig implements Serializable {

    public static final Option<List<FieldReplacer>> FIELD_REPLACER_LIST =
            Options.key("field_replacer_list")
                    .listType(FieldReplacer.class)
                    .noDefaultValue()
                    .withDescription("");

    public static final OptionRule OPTION_RULE =
            OptionRule.builder().required(FIELD_REPLACER_LIST).build();

    private List<FieldReplacer> fieldReplacers;

    @Data
    public static class FieldReplacer implements Serializable {
        @JsonAlias("table_path")
        private String tablePath;

        @JsonAlias("replace_field")
        private String replaceField;

        // TODO remove this after all the old configs are updated
        @Deprecated
        @Getter(AccessLevel.PRIVATE)
        @JsonAlias("pattern")
        private String pattern;

        // TODO remove this after all the old configs are updated
        @Deprecated
        @Getter(AccessLevel.PRIVATE)
        @JsonAlias("replacement")
        private String replacement;

        @JsonAlias("replacements")
        private LinkedHashMap<String, String> replacements;

        @JsonAlias("is_regex")
        private Boolean isRegex;

        @JsonAlias("replace_null")
        private Boolean replaceNull;

        @JsonAlias("replace_first")
        private Boolean replaceFirst;

        @JsonAlias("replace_to_null")
        private Boolean replaceToNull;

        private transient List<String> reversedReplacementsKey;

        public LinkedHashMap<String, String> getReplacements() {
            if (Strings.isNotBlank(pattern) && Strings.isNotBlank(replacement)) {
                // TODO remove this after all the old configs are updated
                return new LinkedHashMap<>(Collections.singletonMap(pattern, replacement));
            }
            return replacements;
        }

        public List<String> getReversedReplacementsKey() {
            if (reversedReplacementsKey == null) {
                reversedReplacementsKey = new ArrayList<>(replacements.keySet());
                Collections.reverse(reversedReplacementsKey);
            }
            return reversedReplacementsKey;
        }
    }

    public static FieldReplacerTransformConfig of(ReadonlyConfig config) {
        FieldReplacerTransformConfig fieldReplacerTransformConfig =
                new FieldReplacerTransformConfig();
        fieldReplacerTransformConfig.setFieldReplacers(config.get(FIELD_REPLACER_LIST));
        return fieldReplacerTransformConfig;
    }
}
