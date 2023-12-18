package org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.config;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class OracleTableConfig implements Serializable {
    private String table;
    private List<String> primaryKeys;
}
