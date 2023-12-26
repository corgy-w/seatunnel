package org.apache.seatunnel.connectors.seatunnel.file.source.reader;

import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.file.config.BaseSourceConfigOptions;
import org.apache.seatunnel.connectors.seatunnel.file.config.CompressFormat;
import org.apache.seatunnel.connectors.seatunnel.file.config.HadoopConf;
import org.apache.seatunnel.connectors.seatunnel.file.exception.FileConnectorException;
import org.apache.seatunnel.format.json.debezium.DebeziumJsonDeserializationSchema;

import io.airlift.compress.lzo.LzopCodec;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class DebeziumJsonReadStrategy extends AbstractReadStrategy {

    private DebeziumJsonDeserializationSchema deserializationSchema;
    private CompressFormat compressFormat = BaseSourceConfigOptions.COMPRESS_CODEC.defaultValue();

    @Override
    public void init(HadoopConf conf) {
        super.init(conf);
        if (pluginConfig.hasPath(BaseSourceConfigOptions.COMPRESS_CODEC.key())) {
            String compressCodec =
                    pluginConfig.getString(BaseSourceConfigOptions.COMPRESS_CODEC.key());
            compressFormat = CompressFormat.valueOf(compressCodec.toUpperCase());
        }
        if (isMergePartition) {
            log.warn("DebeziumJsonReadStrategy not support merge partition");
        }
    }

    @Override
    public void setSeaTunnelRowTypeInfo(SeaTunnelRowType seaTunnelRowType) {
        super.setSeaTunnelRowTypeInfo(seaTunnelRowType);
        deserializationSchema =
                new DebeziumJsonDeserializationSchema(seaTunnelRowType, false, true);
    }

    @Override
    public void read(String path, String tableId, Collector<SeaTunnelRow> output)
            throws IOException, FileConnectorException {
        InputStream inputStream;
        switch (compressFormat) {
            case LZO:
                LzopCodec lzo = new LzopCodec();
                inputStream = lzo.createInputStream(hadoopFileSystemProxy.getInputStream(path));
                break;
            case NONE:
                inputStream = hadoopFileSystemProxy.getInputStream(path);
                break;
            default:
                log.warn(
                        "Text file does not support this compress type: {}",
                        compressFormat.getCompressCodec());
                inputStream = hadoopFileSystemProxy.getInputStream(path);
                break;
        }

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            reader.lines()
                    .forEach(
                            line -> {
                                try {
                                    List<SeaTunnelRow> seaTunnelRows =
                                            deserializationSchema.deserializeList(line.getBytes());
                                    for (SeaTunnelRow seaTunnelRow : seaTunnelRows) {
                                        seaTunnelRow.setTableId(tableId);
                                        output.collect(seaTunnelRow);
                                    }
                                } catch (IOException e) {
                                    String errorMsg =
                                            String.format(
                                                    "Read data from this file [%s] failed", path);
                                    throw new FileConnectorException(
                                            CommonErrorCodeDeprecated.FILE_OPERATION_FAILED,
                                            errorMsg);
                                }
                            });
        }
    }

    @Override
    public SeaTunnelRowType getSeaTunnelRowTypeInfo(String path) throws FileConnectorException {
        throw new FileConnectorException(
                CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                "User must defined schema for json file type");
    }
}
