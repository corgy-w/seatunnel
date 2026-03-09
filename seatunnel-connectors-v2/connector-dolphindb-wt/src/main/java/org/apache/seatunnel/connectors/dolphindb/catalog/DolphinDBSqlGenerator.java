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

package org.apache.seatunnel.connectors.dolphindb.catalog;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

public class DolphinDBSqlGenerator {

    public static String generateDeleteRowSql(
            String database, String table, SeaTunnelRowType seaTunnelRowType) {
        String[] fieldNames = seaTunnelRowType.getFieldNames();
        int[] conditionFieldIndexes = new int[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            conditionFieldIndexes[i] = i;
        }
        return generateDeleteRowSql(table, seaTunnelRowType, conditionFieldIndexes);
    }

    public static String generateDeleteRowSql(
            String table, SeaTunnelRowType seaTunnelRowType, int[] conditionFieldIndexes) {
        StringBuilder deleteSql = new StringBuilder("delete from ").append(table).append(" where ");
        String[] fieldNames = seaTunnelRowType.getFieldNames();
        for (int i = 0; i < conditionFieldIndexes.length; i++) {
            int fieldIndex = conditionFieldIndexes[i];
            if (seaTunnelRowType.getFieldType(fieldIndex).equals(BasicType.FLOAT_TYPE)) {
                deleteSql.append(fieldNames[fieldIndex]).append(" = float(?)");
            } else {
                deleteSql.append(fieldNames[fieldIndex]).append(" = ?");
            }
            if (i != conditionFieldIndexes.length - 1) {
                deleteSql.append(" , ");
            }
        }
        return deleteSql.toString();
    }
}
