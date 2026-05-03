package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ElClientTest {

    @ParameterizedTest
    @EnumSource(ElClient.class)
    void isValidator_should_returnFalse_when_clientIsCurrentlyDefined(ElClient client) {
        assertThat(client.isValidator())
                .as("EL client %s must declare isValidator()", client.name())
                .isFalse();
    }
}
