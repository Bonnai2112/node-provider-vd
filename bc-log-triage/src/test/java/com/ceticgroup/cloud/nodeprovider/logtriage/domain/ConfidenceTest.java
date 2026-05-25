package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConfidenceTest {

    @Test
    void isAtLeast_should_returnTrue_whenEqualOrAbove() {
        assertThat(Confidence.of(80).isAtLeast(Confidence.of(80))).isTrue();
        assertThat(Confidence.of(81).isAtLeast(Confidence.of(80))).isTrue();
    }

    @Test
    void isAtLeast_should_returnFalse_whenBelow() {
        assertThat(Confidence.of(79).isAtLeast(Confidence.of(80))).isFalse();
    }

    @Test
    void of_should_throw_whenOutOfRange() {
        assertThatThrownBy(() -> Confidence.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Confidence.of(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
