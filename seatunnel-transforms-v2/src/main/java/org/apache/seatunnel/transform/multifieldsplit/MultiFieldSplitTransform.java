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

package org.apache.seatunnel.transform.multifieldsplit;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.api.transform.SeaTunnelTransform;
import org.apache.seatunnel.transform.common.MultipleFieldOutputTransform;
import org.apache.seatunnel.transform.common.SeaTunnelRowAccessor;

import com.google.auto.service.AutoService;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@AutoService(SeaTunnelTransform.class)
public class MultiFieldSplitTransform extends MultipleFieldOutputTransform {

    public static String PLUGIN_NAME = "MultiFieldSplit";
    private MultiFieldSplitTransformConfig multiFieldSplitTransformConfig;
    private MultiFieldSplitTransformConfig.SplitOP[] splitOPS;
    private int[] splitFieldIndexS;

    public MultiFieldSplitTransform(
            @NonNull MultiFieldSplitTransformConfig multiFieldSplitTransformConfig,
            @NonNull CatalogTable catalogTable) {
        super(catalogTable);
        this.multiFieldSplitTransformConfig = multiFieldSplitTransformConfig;
        this.splitOPS = multiFieldSplitTransformConfig.getSplitOPS();
        SeaTunnelRowType seaTunnelRowType = catalogTable.getTableSchema().toPhysicalRowDataType();
        this.splitFieldIndexS = buildSplitFieldIndex(seaTunnelRowType);

        this.outputCatalogTable = getProducedCatalogTable();
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected Object[] getOutputFieldValues(SeaTunnelRowAccessor inputRow) {
        List<String> allSplitFieldValues = new ArrayList<>();

        for (int i = 0; i < splitOPS.length; i++) {
            MultiFieldSplitTransformConfig.SplitOP splitOP = splitOPS[i];
            int splitFieldIndex = splitFieldIndexS[i];
            Object splitFieldValue = inputRow.getField(splitFieldIndex);
            if (splitFieldValue == null) {
                allSplitFieldValues.addAll(
                        Arrays.asList(new String[splitOP.getOutputFields().length]));
            } else {
                String[] splitFieldValues =
                        splitFieldValue
                                .toString()
                                .split(splitOP.getSeparator(), splitOP.getOutputFields().length);
                if (splitFieldValues.length < splitOP.getOutputFields().length) {
                    String[] tmp = splitFieldValues;
                    splitFieldValues = new String[splitOP.getOutputFields().length];
                    System.arraycopy(tmp, 0, splitFieldValues, 0, tmp.length);
                }

                allSplitFieldValues.addAll(Arrays.asList(splitFieldValues));
            }
        }

        return allSplitFieldValues.toArray();
    }

    @Override
    protected Column[] getOutputColumns() {
        final ArrayList<Column> columnList = new ArrayList<>();
        for (MultiFieldSplitTransformConfig.SplitOP splitOP : splitOPS) {
            final String splitField = splitOP.getSplitField();
            final String[] outputFields = splitOP.getOutputFields();
            Optional<Column> originColumnOptional =
                    inputCatalogTable.getTableSchema().getColumns().stream()
                            .filter(column -> column.getName().equals(splitField))
                            .findAny();
            if (originColumnOptional.isPresent()) {
                final Column originColumn = originColumnOptional.get();
                for (String outputField : outputFields) {
                    columnList.add(
                            new PhysicalColumn(
                                    outputField,
                                    originColumn.getDataType(),
                                    originColumn.getColumnLength(),
                                    originColumn.getScale(),
                                    originColumn.isNullable(),
                                    originColumn.getDefaultValue(),
                                    originColumn.getComment(),
                                    originColumn.getSourceType(),
                                    originColumn.getOptions()));
                }
            }
        }
        return columnList.toArray(new Column[0]);
    }

    private int[] buildSplitFieldIndex(SeaTunnelRowType rowType) {
        return Arrays.stream(splitOPS)
                .mapToInt(
                        splitOP -> {
                            int index = rowType.indexOf(splitOP.getSplitField());
                            if (index == -1) {
                                throw new IllegalArgumentException(
                                        "Cannot find ["
                                                + splitOP.getSplitField()
                                                + "] field in input row type");
                            }
                            return index;
                        })
                .toArray();
    }
}
