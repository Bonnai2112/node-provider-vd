package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorKey;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase.ImportValidatorKeysCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.ImportValidatorKeysUseCase.KeystoreUpload;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyImporterPort;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportValidatorKeysServiceTest {

    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final DeploymentRef REF = new DeploymentRef("{\"workdir\":\"/tmp/x\"}");
    private static final Instant NOW = Instant.parse("2026-05-04T10:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final List<KeystoreUpload> UPLOADS =
            List.of(new KeystoreUpload("keystore-1.json", "{...}"));

    @Mock private NodeRepository nodes;
    @Mock private ValidatorKeyRepository keys;
    @Mock private ValidatorKeyImporterPort importer;

    @Test
    void importKeys_should_persistKeysAndReturn_when_imported() {
        Node node = nodeWith(true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        String pubkey = "0x" + "a".repeat(96);
        when(importer.importKeystores(eq(REF), anyList(), eq("pw"))).thenReturn(List.of(pubkey));
        ImportValidatorKeysService service =
                new ImportValidatorKeysService(nodes, keys, importer, FIXED);

        List<ValidatorKey> result =
                service.importKeys(new ImportValidatorKeysCommand(NODE_ID, OWNER, UPLOADS, "pw"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pubkey()).isEqualTo(pubkey);
        assertThat(result.get(0).importedAt()).isEqualTo(NOW);
        ArgumentCaptor<List<ValidatorKey>> captor = ArgumentCaptor.forClass(List.class);
        then(keys).should().saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void importKeys_should_throwNotFound_when_ownerMismatch() {
        OwnerId other = new OwnerId(UUID.randomUUID());
        Node node = nodeWith(true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        ImportValidatorKeysService service = new ImportValidatorKeysService(nodes, keys, importer);

        assertThatThrownBy(
                        () ->
                                service.importKeys(
                                        new ImportValidatorKeysCommand(
                                                NODE_ID, other, UPLOADS, "pw")))
                .isInstanceOf(NodeNotFoundException.class);
        then(importer).should(never()).importKeystores(any(), any(), anyString());
    }

    @Test
    void importKeys_should_throw_when_nodeNotConfiguredForValidator() {
        Node node = nodeWith(false, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        ImportValidatorKeysService service = new ImportValidatorKeysService(nodes, keys, importer);

        assertThatThrownBy(
                        () ->
                                service.importKeys(
                                        new ImportValidatorKeysCommand(
                                                NODE_ID, OWNER, UPLOADS, "pw")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validator");
    }

    @Test
    void importKeys_should_throw_when_deploymentNotYetReady() {
        Node node = nodeWith(true, null);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        ImportValidatorKeysService service = new ImportValidatorKeysService(nodes, keys, importer);

        assertThatThrownBy(
                        () ->
                                service.importKeys(
                                        new ImportValidatorKeysCommand(
                                                NODE_ID, OWNER, UPLOADS, "pw")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provisioned");
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
