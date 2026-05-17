package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.in.GenerateTopupDepositUseCase.GenerateTopupDepositCommand;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.TopupDepositGeneratorPort;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenerateTopupDepositServiceTest {

    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final DeploymentRef REF = new DeploymentRef("{\"workdir\":\"/tmp/x\"}");
    private static final String PUBKEY = "0x" + "a".repeat(96);
    private static final String PASSWORD = "test-password-xyz";

    @Mock private NodeRepository nodes;
    @Mock private TopupDepositGeneratorPort generator;

    @Test
    void generate_should_returnDepositJsonBytes_when_happyPath() {
        Node node = nodeWith(Network.HOODI, true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        byte[] expected = "[{\"pubkey\":\"...\"}]".getBytes();
        when(generator.generate(
                        eq(REF), eq("hoodi"), eq(PUBKEY), eq(new BigDecimal("32")), eq(PASSWORD)))
                .thenReturn(expected);
        GenerateTopupDepositService service = new GenerateTopupDepositService(nodes, generator);

        byte[] result =
                service.generate(
                        new GenerateTopupDepositCommand(
                                NODE_ID, OWNER, PUBKEY, new BigDecimal("32"), PASSWORD));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void generate_should_throwNotFound_when_nodeMissing() {
        when(nodes.findById(NODE_ID)).thenReturn(Optional.empty());
        GenerateTopupDepositService service = new GenerateTopupDepositService(nodes, generator);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateTopupDepositCommand(
                                                NODE_ID,
                                                OWNER,
                                                PUBKEY,
                                                new BigDecimal("32"),
                                                PASSWORD)))
                .isInstanceOf(NodeNotFoundException.class);
        then(generator)
                .should(never())
                .generate(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void generate_should_throwNotFound_when_ownerMismatch() {
        Node node = nodeWith(Network.HOODI, true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        OwnerId other = new OwnerId(UUID.randomUUID());
        GenerateTopupDepositService service = new GenerateTopupDepositService(nodes, generator);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateTopupDepositCommand(
                                                NODE_ID,
                                                other,
                                                PUBKEY,
                                                new BigDecimal("32"),
                                                PASSWORD)))
                .isInstanceOf(NodeNotFoundException.class);
        then(generator)
                .should(never())
                .generate(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void generate_should_throw_when_validatorDisabled() {
        Node node = nodeWith(Network.HOODI, false, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        GenerateTopupDepositService service = new GenerateTopupDepositService(nodes, generator);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateTopupDepositCommand(
                                                NODE_ID,
                                                OWNER,
                                                PUBKEY,
                                                new BigDecimal("32"),
                                                PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validator");
    }

    @Test
    void generate_should_throw_when_noDeploymentYet() {
        Node node = nodeWith(Network.HOODI, true, null);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        GenerateTopupDepositService service = new GenerateTopupDepositService(nodes, generator);

        assertThatThrownBy(
                        () ->
                                service.generate(
                                        new GenerateTopupDepositCommand(
                                                NODE_ID,
                                                OWNER,
                                                PUBKEY,
                                                new BigDecimal("32"),
                                                PASSWORD)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generate_should_passSepoliaChainLowercased_when_networkSepolia() {
        Node node = nodeWith(Network.SEPOLIA, true, REF);
        when(nodes.findById(NODE_ID)).thenReturn(Optional.of(node));
        when(generator.generate(any(), eq("sepolia"), anyString(), any(), anyString()))
                .thenReturn(new byte[0]);
        GenerateTopupDepositService service = new GenerateTopupDepositService(nodes, generator);

        service.generate(
                new GenerateTopupDepositCommand(
                        NODE_ID, OWNER, PUBKEY, new BigDecimal("32"), PASSWORD));

        then(generator).should().generate(eq(REF), eq("sepolia"), eq(PUBKEY), any(), eq(PASSWORD));
    }

    @Test
    void command_should_throw_when_amountBelowOneEth() {
        assertThatThrownBy(
                        () ->
                                new GenerateTopupDepositCommand(
                                        NODE_ID, OWNER, PUBKEY, new BigDecimal("0.5"), PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void command_should_accept_oneEthAmount() {
        new GenerateTopupDepositCommand(NODE_ID, OWNER, PUBKEY, new BigDecimal("1"), PASSWORD);
    }

    @Test
    void command_should_throw_when_pubkeyInvalid() {
        assertThatThrownBy(
                        () ->
                                new GenerateTopupDepositCommand(
                                        NODE_ID,
                                        OWNER,
                                        "not-a-pubkey",
                                        new BigDecimal("32"),
                                        PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pubkey");
    }

    @Test
    void command_should_throw_when_passwordBlank() {
        assertThatThrownBy(
                        () ->
                                new GenerateTopupDepositCommand(
                                        NODE_ID, OWNER, PUBKEY, new BigDecimal("32"), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keystorePassword");
    }

    private static Node nodeWith(Network network, boolean validator, DeploymentRef ref) {
        NodeOptions opts =
                new NodeOptions(
                        validator,
                        false,
                        NodeOptions.DEFAULT_FEE_RECIPIENT,
                        Optional.empty(),
                        Optional.empty(),
                        OptionalInt.empty());
        return Node.restore(
                NODE_ID,
                OWNER,
                network,
                ClientPair.besuTeku(),
                opts,
                new NodeStatus.Provisioning(),
                ref);
    }
}
