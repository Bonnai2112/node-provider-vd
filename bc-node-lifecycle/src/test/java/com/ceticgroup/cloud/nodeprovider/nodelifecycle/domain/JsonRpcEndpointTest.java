package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class JsonRpcEndpointTest {

    @Test
    void constructor_should_throw_when_uriIsNull() {
        assertThatThrownBy(() -> new JsonRpcEndpoint(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_should_exposeUri_when_uriIsProvided() {
        URI uri = URI.create("http://localhost:8545");

        JsonRpcEndpoint endpoint = new JsonRpcEndpoint(uri);

        assertThat(endpoint.uri()).isEqualTo(uri);
    }
}
