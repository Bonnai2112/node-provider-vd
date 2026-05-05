package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.DownloadValidatorKeysUseCase;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyArchiverPort;
import java.util.Objects;

public final class DownloadValidatorKeysService implements DownloadValidatorKeysUseCase {

    private final NodeRepository nodes;
    private final ValidatorKeyArchiverPort archiver;

    public DownloadValidatorKeysService(NodeRepository nodes, ValidatorKeyArchiverPort archiver) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        this.archiver = Objects.requireNonNull(archiver, "archiver");
    }

    @Override
    public byte[] downloadKeystores(NodeId nodeId, OwnerId requester) {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(requester, "requester");
        Node node = nodes.findById(nodeId).orElseThrow(() -> new NodeNotFoundException(nodeId));
        if (!node.owner().equals(requester)) {
            throw new NodeNotFoundException(nodeId);
        }
        if (!node.options().validator()) {
            throw new IllegalStateException(
                    "node is not configured for validator; nothing to download");
        }
        DeploymentRef ref = node.deploymentRef();
        if (ref == null) {
            throw new IllegalStateException("node has no deployment yet");
        }
        return archiver.archive(ref);
    }
}
