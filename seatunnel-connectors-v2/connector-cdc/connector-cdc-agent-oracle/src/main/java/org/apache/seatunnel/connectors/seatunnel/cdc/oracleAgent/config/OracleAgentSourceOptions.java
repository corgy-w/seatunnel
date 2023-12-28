package org.apache.seatunnel.connectors.seatunnel.cdc.oracleAgent.config;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.SingleChoiceOption;
import org.apache.seatunnel.connectors.cdc.base.option.SourceOptions;
import org.apache.seatunnel.connectors.cdc.base.option.StartupMode;
import org.apache.seatunnel.connectors.cdc.base.option.StopMode;

import java.util.Arrays;
import java.util.List;

public class OracleAgentSourceOptions {

    public static final Option<String> ORACLE9BRIDGE_AGENT_HOST =
            Options.key("oracle-agent-host")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("The host of the Oracle agent. e.g. localhost");

    public static final Option<Integer> ORACLE9BRIDGE_AGENT_PORT =
            Options.key("oracle-agent-port")
                    .intType()
                    .noDefaultValue()
                    .withDescription("The port of the Oracle agent. e.g. 8190");

    public static final SingleChoiceOption<StartupMode> STARTUP_MODE =
            (SingleChoiceOption<StartupMode>)
                    Options.key(SourceOptions.STARTUP_MODE_KEY)
                            .singleChoice(
                                    StartupMode.class,
                                    Arrays.asList(
                                            StartupMode.INITIAL,
                                            StartupMode.EARLIEST,
                                            StartupMode.LATEST))
                            .defaultValue(StartupMode.INITIAL)
                            .withDescription(
                                    "Optional startup mode for CDC source, valid enumerations are "
                                            + "\"initial\", \"earliest\", \"latest\"");

    public static final SingleChoiceOption<StopMode> STOP_MODE =
            (SingleChoiceOption<StopMode>)
                    Options.key(SourceOptions.STOP_MODE_KEY)
                            .singleChoice(StopMode.class, Arrays.asList(StopMode.NEVER))
                            .defaultValue(StopMode.NEVER)
                            .withDescription("Optional stop mode for CDC source");

    public static final Option<List<OracleTableConfig>> TABLE_NAMES_CONFIG =
            Options.key("table_names_config")
                    .listType(OracleTableConfig.class)
                    .noDefaultValue()
                    .withDescription(
                            "Config table configs. example: "
                                    + "["
                                    + "   {"
                                    + "       \"table\": \"db1.schema1.table1\","
                                    + "       \"primaryKeys\": [\"key1\",\"key2\"]"
                                    + "   }"
                                    + "]");
}
