package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathAllowlistTest {

    @Test
    void permits_should_acceptPathStartingWithAnyAllowedPrefix() {
        PathAllowlist allowlist = new PathAllowlist(List.of("src/main/java/", "src/test/java/"));

        assertThat(allowlist.permits("src/main/java/com/Foo.java")).isTrue();
        assertThat(allowlist.permits("src/test/java/com/FooTest.java")).isTrue();
    }

    @Test
    void permits_should_rejectPathOutsideAllowedPrefixes() {
        PathAllowlist allowlist = new PathAllowlist(List.of("src/main/java/"));

        assertThat(allowlist.permits(".gitlab-ci.yml")).isFalse();
        assertThat(allowlist.permits("infra/secrets/keystore.p12")).isFalse();
        assertThat(allowlist.permits("pom.xml")).isFalse();
    }

    @Test
    void permits_should_rejectPathWithParentTraversal() {
        PathAllowlist allowlist = new PathAllowlist(List.of("src/"));

        assertThat(allowlist.permits("src/../etc/passwd")).isFalse();
        assertThat(allowlist.permits("src/main/../../etc/passwd")).isFalse();
    }

    @Test
    void permits_should_normalizeLeadingSlashesAndDotSegments() {
        PathAllowlist allowlist = new PathAllowlist(List.of("src/main/java/"));

        assertThat(allowlist.permits("/src/main/java/com/Foo.java")).isTrue();
        assertThat(allowlist.permits("./src/main/java/com/Foo.java")).isTrue();
    }

    @Test
    void permits_should_treatBackslashAsForwardSlash() {
        PathAllowlist allowlist = new PathAllowlist(List.of("src/main/java/"));

        assertThat(allowlist.permits("src\\main\\java\\com\\Foo.java")).isTrue();
    }

    @Test
    void constructor_should_throw_whenAllowlistIsEmpty() {
        assertThatThrownBy(() -> new PathAllowlist(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permitsAll_should_returnFalse_whenAnyPatchEscapesAllowlist() {
        PathAllowlist allowlist = new PathAllowlist(List.of("src/main/java/"));
        FilePatch ok = new FilePatch("src/main/java/com/Foo.java", "...");
        FilePatch bad = new FilePatch("pom.xml", "...");

        assertThat(allowlist.permitsAll(List.of(ok))).isTrue();
        assertThat(allowlist.permitsAll(List.of(ok, bad))).isFalse();
    }
}
