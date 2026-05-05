package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import java.util.Objects;

public record ProvisionNodeCommand(
        OwnerId owner, Network network, ClientPair clientPair, NodeOptions options) {

    public ProvisionNodeCommand {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(clientPair, "clientPair");
        Objects.requireNonNull(options, "options");
    }
}
