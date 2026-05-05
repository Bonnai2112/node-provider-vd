package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ConsensusSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ExecutionSyncStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.JsonRpcEndpoint;
import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;

public interface BlockchainProbePort {

    Optional<ExecutionSyncStatus> probeElSync(JsonRpcEndpoint endpoint);

    OptionalInt probePeers(JsonRpcEndpoint endpoint);

    Optional<ConsensusSyncStatus> probeClSync(URI clRestEndpoint);
}
