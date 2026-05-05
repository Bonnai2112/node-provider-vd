package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.DeploymentRef;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeOptions;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyArchiverPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DownloadValidatorKeysServiceTest {

    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final DeploymentRef REF = new DeploymentRef("{\"workdir\":\"/tmp/x\"}");

    @Mock private NodeRepository nodes;
    @Mock private ValidatorKeyArchiverPort archiver;

    @Test
    void download_should_returnZipBytes_when_ownerMatchesAndValidatorEnabled() {
        Node node = nodeWith(true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        byte[] zip = new byte[] {0x50, 0x4b, 0x03, 0x04};
        when(archiver.archive(REF)).thenReturn(zip);
        DownloadValidatorKeysService service = new DownloadValidatorKeysService(nodes, archiver);

        byte[] result = service.downloadKeystores(NODE_ID, OWNER);

        assertThat(result).isEqualTo(zip);
    }

    @Test
    void download_should_throwNotFound_when_ownerMismatch() {
        Node node = nodeWith(true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        DownloadValidatorKeysService service = new DownloadValidatorKeysService(nodes, archiver);

        assertThatThrownBy(() -> service.downloadKeystores(NODE_ID, new OwnerId(UUID.randomUUID())))
                .isInstanceOf(NodeNotFoundException.class);
        then(archiver).should(never()).archive(any());
    }

    @Test
    void download_should_throw_when_validatorDisabled() {
        Node node = nodeWith(false, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        DownloadValidatorKeysService service = new DownloadValidatorKeysService(nodes, archiver);

        assertThatThrownBy(() -> service.downloadKeystores(NODE_ID, OWNER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validator");
    }

    @Test
    void download_should_throw_when_noDeploymentYet() {
        Node node = nodeWith(true, null);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        DownloadValidatorKeysService service = new DownloadValidatorKeysService(nodes, archiver);

        assertThatThrownBy(() -> service.downloadKeystores(NODE_ID, OWNER))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Node nodeWith(boolean validator, DeploymentRef ref) {
        NodeOptions opts =
                new NodeOptions(
                        validator,
                        false,
                        NodeOptions.DEFAULT_FEE_RECIPIENT,
                        Optional.empty(),
                        Optional.empty(),
                        java.util.OptionalInt.empty());
        return Node.restore(
                NODE_ID,
                OWNER,
                Network.HOODI,
                ClientPair.besuTeku(),
                opts,
                new NodeStatus.Provisioning(),
                ref);
    }
}
