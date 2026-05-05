package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NodeSpecTest {

    @Test
    void constructor_should_exposeAllFields_when_allProvided() {
        NodeId id = new NodeId(UUID.randomUUID());
        OwnerId owner = new OwnerId(UUID.randomUUID());
        NodeOptions options = NodeOptions.defaults();

        NodeSpec spec = new NodeSpec(id, owner, Network.SEPOLIA, ClientPair.besuTeku(), options);

        assertThat(spec.nodeId()).isEqualTo(id);
        assertThat(spec.owner()).isEqualTo(owner);
        assertThat(spec.network()).isEqualTo(Network.SEPOLIA);
        assertThat(spec.clientPair()).isEqualTo(ClientPair.besuTeku());
        assertThat(spec.options()).isEqualTo(options);
    }

    @Test
    void constructor_should_throw_when_nodeIdIsNull() {
        assertThatThrownBy(
                        () ->
                                new NodeSpec(
                                        null,
                                        new OwnerId(UUID.randomUUID()),
                                        Network.HOODI,
                                        ClientPair.besuTeku(),
                                        NodeOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_throw_when_ownerIsNull() {
        assertThatThrownBy(
                        () ->
                                new NodeSpec(
                                        new NodeId(UUID.randomUUID()),
                                        null,
                                        Network.HOODI,
                                        ClientPair.besuTeku(),
                                        NodeOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_throw_when_networkIsNull() {
        assertThatThrownBy(
                        () ->
                                new NodeSpec(
                                        new NodeId(UUID.randomUUID()),
                                        new OwnerId(UUID.randomUUID()),
                                        null,
                                        ClientPair.besuTeku(),
                                        NodeOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_throw_when_clientPairIsNull() {
        assertThatThrownBy(
                        () ->
                                new NodeSpec(
                                        new NodeId(UUID.randomUUID()),
                                        new OwnerId(UUID.randomUUID()),
                                        Network.HOODI,
                                        null,
                                        NodeOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_throw_when_optionsAreNull() {
        assertThatThrownBy(
                        () ->
                                new NodeSpec(
                                        new NodeId(UUID.randomUUID()),
                                        new OwnerId(UUID.randomUUID()),
                                        Network.HOODI,
                                        ClientPair.besuTeku(),
                                        null))
                .isInstanceOf(NullPointerException.class);
    }
}
