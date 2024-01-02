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

package org.apache.seatunnel.transform.fieldrenamer;

import org.apache.seatunnel.shade.com.fasterxml.jackson.annotation.JsonAlias;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class FieldRenamerConfig implements Serializable {

    public static final Option<String> TABLE_MATCH_REGEX =
            Options.key("table_match_regex")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("The table name match regex");

    public static final Option<ConvertCase> CONVERT_CASE =
            Options.key("convert_case")
                    .enumType(ConvertCase.class)
                    .noDefaultValue()
                    .withDescription("Convert to uppercase or lowercase");

    public static final Option<String> PREFIX =
            Options.key("prefix")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Add prefix for field name");

    public static final Option<String> SUFFIX =
            Options.key("suffix")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Add suffix for field name");

    public static final Option<String> REPLACE_FROM =
            Options.key("replace_from")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("The regex of replace field name from");

    public static final Option<String> REPLACE_TO =
            Options.key("replace_to")
                    .stringType()
                    .defaultValue("")
                    .withDescription("The regex of replace field name to ");

    public static final Option<List<SpecificModify>> SPECIFIC =
            Options.key("specific")
                    .listType(SpecificModify.class)
                    .noDefaultValue()
                    .withDescription("The specific modify field name");

    @JsonAlias("table_match_regex")
    private String tableMatchRegex;

    @JsonAlias("convert_case")
    private ConvertCase convertCase;

    @JsonAlias("prefix")
    private String prefix;

    @JsonAlias("suffix")
    private String suffix;

    @JsonAlias("replace_from")
    private String replaceFrom;

    @JsonAlias("replace_to")
    private String replaceTo;

    @JsonAlias("specific")
    private List<SpecificModify> specific;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SpecificModify implements Serializable {
        @JsonAlias("table_name")
        private String tableName;

        @JsonAlias("field_name")
        private String fieldName;

        @JsonAlias("target_name")
        private String targetName;
    }

    public static FieldRenamerConfig of(ReadonlyConfig config) {
        FieldRenamerConfig fieldRenamerConfig = new FieldRenamerConfig();
        fieldRenamerConfig.setTableMatchRegex(config.get(TABLE_MATCH_REGEX));
        fieldRenamerConfig.setConvertCase(config.get(CONVERT_CASE));
        fieldRenamerConfig.setPrefix(config.get(PREFIX));
        fieldRenamerConfig.setSuffix(config.get(SUFFIX));
        fieldRenamerConfig.setReplaceFrom(config.get(REPLACE_FROM));
        fieldRenamerConfig.setReplaceTo(config.get(REPLACE_TO));
        fieldRenamerConfig.setSpecific(config.get(SPECIFIC));
        return fieldRenamerConfig;
    }
}
