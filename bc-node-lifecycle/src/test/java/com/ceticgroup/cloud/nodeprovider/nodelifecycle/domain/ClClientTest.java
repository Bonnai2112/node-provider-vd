package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClClientTest {

    @ParameterizedTest
    @EnumSource(ClClient.class)
    void isValidator_should_returnFalse_when_clientIsCurrentlyDefined(ClClient client) {
        assertThat(client.isValidator())
                .as("CL client %s must declare isValidator()", client.name())
                .isFalse();
    }
}
