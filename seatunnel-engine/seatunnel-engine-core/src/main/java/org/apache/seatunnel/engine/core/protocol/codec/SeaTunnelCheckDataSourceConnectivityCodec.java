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

package org.apache.seatunnel.engine.core.protocol.codec;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.Generated;
import com.hazelcast.client.impl.protocol.codec.builtin.CodecUtil;
import com.hazelcast.client.impl.protocol.codec.builtin.DataCodec;
import com.hazelcast.client.impl.protocol.codec.builtin.StringCodec;
import com.hazelcast.internal.serialization.Data;

import javax.annotation.Nullable;

import static com.hazelcast.client.impl.protocol.ClientMessage.PARTITION_ID_FIELD_OFFSET;
import static com.hazelcast.client.impl.protocol.ClientMessage.RESPONSE_BACKUP_ACKS_FIELD_OFFSET;
import static com.hazelcast.client.impl.protocol.ClientMessage.TYPE_FIELD_OFFSET;
import static com.hazelcast.client.impl.protocol.ClientMessage.UNFRAGMENTED_MESSAGE;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.BYTE_SIZE_IN_BYTES;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.INT_SIZE_IN_BYTES;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.decodeInt;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.encodeInt;

@Generated("custom")
public final class SeaTunnelCheckDataSourceConnectivityCodec {
    // hex: 0xDE1300
    public static final int REQUEST_MESSAGE_TYPE = 14554112;
    // hex: 0xDE1301
    public static final int RESPONSE_MESSAGE_TYPE = 14554113;

    private static final int REQUEST_TIMEOUT_MS_FIELD_OFFSET =
            PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_INITIAL_FRAME_SIZE =
            REQUEST_TIMEOUT_MS_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE =
            RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;

    private SeaTunnelCheckDataSourceConnectivityCodec() {}

    public static class RequestParameters {
        public @Nullable String catalogIdentifier;
        public @Nullable String optionsJson;
        public int timeoutMs;
    }

    public static ClientMessage encodeRequest(
            @Nullable String catalogIdentifier, @Nullable String optionsJson, int timeoutMs) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setRetryable(true);
        clientMessage.setOperationName("SeaTunnel.CheckDataSourceConnectivity");
        ClientMessage.Frame initialFrame =
                new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        encodeInt(initialFrame.content, REQUEST_TIMEOUT_MS_FIELD_OFFSET, timeoutMs);
        clientMessage.add(initialFrame);
        CodecUtil.encodeNullable(clientMessage, catalogIdentifier, StringCodec::encode);
        CodecUtil.encodeNullable(clientMessage, optionsJson, StringCodec::encode);
        return clientMessage;
    }

    public static RequestParameters decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        RequestParameters request = new RequestParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        request.timeoutMs = decodeInt(initialFrame.content, REQUEST_TIMEOUT_MS_FIELD_OFFSET);
        request.catalogIdentifier = CodecUtil.decodeNullable(iterator, StringCodec::decode);
        request.optionsJson = CodecUtil.decodeNullable(iterator, StringCodec::decode);
        return request;
    }

    public static ClientMessage encodeResponse(Data response) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame =
                new ClientMessage.Frame(
                        new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        clientMessage.add(initialFrame);
        DataCodec.encode(clientMessage, response);
        return clientMessage;
    }

    public static Data decodeResponse(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        iterator.next();
        return DataCodec.decode(iterator);
    }
}
