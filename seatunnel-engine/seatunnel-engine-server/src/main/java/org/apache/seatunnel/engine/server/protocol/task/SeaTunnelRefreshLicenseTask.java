package org.apache.seatunnel.engine.server.protocol.task;

import org.apache.seatunnel.engine.core.protocol.codec.SeaTunnelRefreshLicenseCodec;
import org.apache.seatunnel.engine.server.operation.RefreshLicenseOperation;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.spi.impl.operationservice.Operation;

public class SeaTunnelRefreshLicenseTask extends AbstractSeaTunnelMessageTask<Void, Void> {

    protected SeaTunnelRefreshLicenseTask(
            ClientMessage clientMessage, Node node, Connection connection) {
        super(
                clientMessage,
                node,
                connection,
                m -> null,
                x -> SeaTunnelRefreshLicenseCodec.encodeResponse());
    }

    @Override
    protected Operation prepareOperation() {
        return new RefreshLicenseOperation();
    }

    @Override
    public String getMethodName() {
        return "refreshLicense";
    }

    @Override
    public Object[] getParameters() {
        return new Object[0];
    }
}
