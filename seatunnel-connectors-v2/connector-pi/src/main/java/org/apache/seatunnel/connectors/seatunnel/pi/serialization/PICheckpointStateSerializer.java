package org.apache.seatunnel.connectors.seatunnel.pi.serialization;

import org.apache.seatunnel.api.serialization.Serializer;
import org.apache.seatunnel.connectors.seatunnel.pi.split.PICheckpointState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * PI checkpoint state serializer
 *
 * <p>Used for checkpoint mechanism, serializing and deserializing PICheckpointState objects
 */
public class PICheckpointStateSerializer implements Serializer<PICheckpointState> {

    private static final int VERSION = 1;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public byte[] serialize(PICheckpointState state) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            // Write version number
            oos.writeInt(VERSION);

            // Write checkpoint ID
            oos.writeLong(state.getCheckpointId());

            // Write WebID timestamp mapping
            Map<String, LocalDateTime> webIdTimestamps = state.getWebIdTimestamps();
            oos.writeInt(webIdTimestamps.size());
            for (Map.Entry<String, LocalDateTime> entry : webIdTimestamps.entrySet()) {
                oos.writeUTF(entry.getKey());
                oos.writeUTF(entry.getValue().format(FORMATTER));
            }

            // Write disconnection start time
            LocalDateTime disconnectStartTime = state.getDisconnectStartTime();
            if (disconnectStartTime != null) {
                oos.writeBoolean(true);
                oos.writeUTF(disconnectStartTime.format(FORMATTER));
            } else {
                oos.writeBoolean(false);
            }

            // Write last message time
            LocalDateTime lastMessageTime = state.getLastMessageTime();
            if (lastMessageTime != null) {
                oos.writeBoolean(true);
                oos.writeUTF(lastMessageTime.format(FORMATTER));
            } else {
                oos.writeBoolean(false);
            }

            oos.flush();
            return baos.toByteArray();
        }
    }

    @Override
    public PICheckpointState deserialize(byte[] serialized) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
                ObjectInputStream ois = new ObjectInputStream(bais)) {

            // Read version number
            int version = ois.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported serialization version: " + version);
            }

            PICheckpointState state = new PICheckpointState();

            // Read checkpoint ID
            state.setCheckpointId(ois.readLong());

            // Read WebID timestamp mapping
            int webIdTimestampsSize = ois.readInt();
            for (int i = 0; i < webIdTimestampsSize; i++) {
                String webId = ois.readUTF();
                String timestampStr = ois.readUTF();
                LocalDateTime timestamp = LocalDateTime.parse(timestampStr, FORMATTER);
                state.updateState(webId, timestamp);
            }

            // Read disconnection start time
            if (ois.readBoolean()) {
                String disconnectStartTimeStr = ois.readUTF();
                LocalDateTime disconnectStartTime =
                        LocalDateTime.parse(disconnectStartTimeStr, FORMATTER);
                state.setDisconnectStartTime(disconnectStartTime);
            }

            // Read last message time
            if (ois.readBoolean()) {
                String lastMessageTimeStr = ois.readUTF();
                LocalDateTime lastMessageTime = LocalDateTime.parse(lastMessageTimeStr, FORMATTER);
                state.setLastMessageTime(lastMessageTime);
            }

            return state;
        }
    }
}
