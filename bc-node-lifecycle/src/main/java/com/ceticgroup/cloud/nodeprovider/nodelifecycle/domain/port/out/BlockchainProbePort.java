package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.SyncStatus;

public interface BlockchainProbePort {

    SyncStatus probeSync(JsonRpcEndpoint endpoint);

    int probePeers(JsonRpcEndpoint endpoint);
}
