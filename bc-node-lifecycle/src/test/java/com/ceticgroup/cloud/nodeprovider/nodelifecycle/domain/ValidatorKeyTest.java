package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidatorKeyTest {

    private static final UUID ID = UUID.randomUUID();
    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final String VALID_PUBKEY = "0x" + "a".repeat(96);
    private static final Instant NOW = Instant.parse("2026-05-04T10:00:00Z");

    @Test
    void constructor_should_acceptValidPubkey() {
        ValidatorKey key = new ValidatorKey(ID, NODE_ID, VALID_PUBKEY, NOW);

        assertThat(key.pubkey()).isEqualTo(VALID_PUBKEY);
    }

    @Test
    void constructor_should_throw_when_pubkeyHasNoPrefix() {
        assertThatThrownBy(() -> new ValidatorKey(ID, NODE_ID, "a".repeat(96), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_throw_when_pubkeyTooShort() {
        assertThatThrownBy(() -> new ValidatorKey(ID, NODE_ID, "0xabc", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_throw_when_pubkeyContainsNonHex() {
        assertThatThrownBy(() -> new ValidatorKey(ID, NODE_ID, "0x" + "z".repeat(96), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_should_throw_when_anyArgIsNull() {
        assertThatThrownBy(() -> new ValidatorKey(null, NODE_ID, VALID_PUBKEY, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidatorKey(ID, null, VALID_PUBKEY, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidatorKey(ID, NODE_ID, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidatorKey(ID, NODE_ID, VALID_PUBKEY, null))
                .isInstanceOf(NullPointerException.class);
    }
}
