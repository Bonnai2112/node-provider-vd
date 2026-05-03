package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClientPairTest {

    @Test
    void besuTeku_should_returnPairWithBesuAndTeku() {
        ClientPair pair = ClientPair.besuTeku();

        assertThat(pair.executionLayer()).isEqualTo(ElClient.BESU);
        assertThat(pair.consensusLayer()).isEqualTo(ClClient.TEKU);
    }

    @Test
    void constructor_should_accept_when_besuAndTeku() {
        ClientPair pair = new ClientPair(ElClient.BESU, ClClient.TEKU);

        assertThat(pair).isNotNull();
    }

    @Test
    void constructor_should_throw_when_executionLayerIsNull() {
        assertThatThrownBy(() -> new ClientPair(null, ClClient.TEKU))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_throw_when_consensusLayerIsNull() {
        assertThatThrownBy(() -> new ClientPair(ElClient.BESU, null))
                .isInstanceOf(NullPointerException.class);
    }
}
