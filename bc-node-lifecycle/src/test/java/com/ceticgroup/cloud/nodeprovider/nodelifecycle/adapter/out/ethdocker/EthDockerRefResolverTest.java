package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EthDockerRefResolverTest {

    private static final String REPO = "https://example.invalid/eth-docker.git";
    private static final String TAG = "v26.4.1";
    private static final String SHA = "afbd3212d1a657cc3d3bf731d596b66af8834f5d";

    @Test
    void resolve_should_returnFreshSha_andUpdateCache_when_remoteSucceeds(@TempDir Path tmp) {
        Path cache = tmp.resolve("sha");
        EthDockerRefResolver resolver = new EthDockerRefResolver(stubReturning(SHA), cache);

        EthDockerRef ref = resolver.resolve(REPO, TAG);

        assertThat(ref.tag()).isEqualTo(TAG);
        assertThat(ref.sha()).isEqualTo(SHA);
        assertThat(cache).exists();
    }

    @Test
    void resolve_should_returnCachedSha_when_remoteFails_andCacheExists(@TempDir Path tmp)
            throws IOException {
        Path cache = tmp.resolve("sha");
        Files.writeString(cache, TAG + " " + SHA, StandardCharsets.UTF_8);
        EthDockerRefResolver resolver = new EthDockerRefResolver(stubReturning(null), cache);

        EthDockerRef ref = resolver.resolve(REPO, TAG);

        assertThat(ref.sha()).isEqualTo(SHA);
    }

    @Test
    void resolve_should_throw_when_remoteFails_andCacheMissing(@TempDir Path tmp) {
        Path cache = tmp.resolve("sha");
        EthDockerRefResolver resolver = new EthDockerRefResolver(stubReturning(null), cache);

        assertThatThrownBy(() -> resolver.resolve(REPO, TAG))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no cache found");
    }

    @Test
    void resolve_should_ignoreCache_when_tagMismatch(@TempDir Path tmp) throws IOException {
        Path cache = tmp.resolve("sha");
        Files.writeString(cache, "v0.0.0 oldsha", StandardCharsets.UTF_8);
        EthDockerRefResolver resolver = new EthDockerRefResolver(stubReturning(null), cache);

        assertThatThrownBy(() -> resolver.resolve(REPO, TAG))
                .isInstanceOf(IllegalStateException.class);
    }

    private static GitLsRemoteClient stubReturning(String sha) {
        return (repoUrl, ref) -> sha == null ? Optional.empty() : Optional.of(sha);
    }
}
