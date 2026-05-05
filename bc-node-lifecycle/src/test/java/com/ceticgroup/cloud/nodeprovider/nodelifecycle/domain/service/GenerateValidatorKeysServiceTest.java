package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateValidatorKeysUseCase.GenerateValidatorKeysResult;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.ValidatorKeyGeneratorPort;
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
class GenerateValidatorKeysServiceTest {

    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final DeploymentRef REF = new DeploymentRef("{\"workdir\":\"/tmp/x\"}");
    private static final Instant NOW = Instant.parse("2026-05-04T10:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String FIXED_PASSWORD = "test-password-xyz";
    private static final String WITHDRAWAL = "0x" + "a".repeat(40);

    @Mock private NodeRepository nodes;
    @Mock private ValidatorKeyRepository keys;
    @Mock private ValidatorKeyGeneratorPort generator;
    @Mock private ValidatorKeyImporterPort importer;

    @Test
    void generate_should_returnMnemonicPasswordAndKeys_when_happyPath() {
        Node node = nodeWith(true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        String pubkey1 = "0x" + "a".repeat(96);
        String pubkey2 = "0x" + "b".repeat(96);
        when(generator.generate(eq(REF), eq("hoodi"), eq(2), eq(WITHDRAWAL), anyString()))
                .thenReturn(
                        new ValidatorKeyGeneratorPort.GeneratedKeys(
                                "abandon ability ...", List.of(pubkey1, pubkey2)));
        GenerateValidatorKeysService service =
                new GenerateValidatorKeysService(
                        nodes, keys, generator, importer, FIXED, () -> FIXED_PASSWORD);

        GenerateValidatorKeysResult result =
                service.generate(new GenerateValidatorKeysCommand(NODE_ID, OWNER, 2, WITHDRAWAL));

        assertThat(result.mnemonic()).isEqualTo("abandon ability ...");
        assertThat(result.password()).isEqualTo(FIXED_PASSWORD);
        assertThat(result.keys()).hasSize(2);
        assertThat(result.keys())
                .extracting(ValidatorKey::pubkey)
                .containsExactly(pubkey1, pubkey2);
        then(importer).should().triggerImport(REF, FIXED_PASSWORD);
        ArgumentCaptor<List<ValidatorKey>> captor = ArgumentCaptor.forClass(List.class);
        then(keys).should().saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void generate_should_throwNotFound_when_ownerMismatch() {
        Node node = nodeWith(true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        OwnerId other = new OwnerId(UUID.randomUUID());
        GenerateValidatorKeysService service =
                new GenerateValidatorKeysService(nodes, keys, generator, importer);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateValidatorKeysCommand(
                                                NODE_ID, other, 1, WITHDRAWAL)))
                .isInstanceOf(NodeNotFoundException.class);
        then(generator)
                .should(never())
                .generate(any(), anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void generate_should_throw_when_nodeHasValidatorDisabled() {
        Node node = nodeWith(false, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        GenerateValidatorKeysService service =
                new GenerateValidatorKeysService(nodes, keys, generator, importer);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateValidatorKeysCommand(
                                                NODE_ID, OWNER, 1, WITHDRAWAL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validator");
    }

    @Test
    void generate_should_throw_when_deploymentNotYetReady() {
        Node node = nodeWith(true, null);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        GenerateValidatorKeysService service =
                new GenerateValidatorKeysService(nodes, keys, generator, importer);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateValidatorKeysCommand(
                                                NODE_ID, OWNER, 1, WITHDRAWAL)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void command_should_throw_when_countOutOfRange() {
        assertThatThrownBy(() -> new GenerateValidatorKeysCommand(NODE_ID, OWNER, 0, WITHDRAWAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenerateValidatorKeysCommand(NODE_ID, OWNER, 1000, WITHDRAWAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void command_should_throw_when_withdrawalAddressInvalid() {
        assertThatThrownBy(
                        () -> new GenerateValidatorKeysCommand(NODE_ID, OWNER, 1, "not-an-address"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new GenerateValidatorKeysCommand(
                                        NODE_ID, OWNER, 1, "0x" + "z".repeat(40)))
                .isInstanceOf(IllegalArgumentException.class);
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
