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

package org.apache.seatunnel.transform.fieldremover;

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.type.TypeReference;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class FieldRemoverConfig implements Serializable {

    public static final Option<Map<String, List<String>>> REMOVED_FIELDS =
            Options.key("removed_fields")
                    .type(new TypeReference<Map<String, List<String>>>() {})
                    .noDefaultValue()
                    .withDescription("The fields to be removed");

    private Map<String, List<String>> removedFields;

    public static FieldRemoverConfig of(ReadonlyConfig config) {
        FieldRemoverConfig fieldRemoverConfig = new FieldRemoverConfig();
        fieldRemoverConfig.setRemovedFields(config.get(REMOVED_FIELDS));
        return fieldRemoverConfig;
    }
}
