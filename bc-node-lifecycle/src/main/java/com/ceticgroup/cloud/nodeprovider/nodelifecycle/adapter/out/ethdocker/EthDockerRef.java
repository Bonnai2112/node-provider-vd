package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.util.Objects;

public record EthDockerRef(String tag, String sha) {

    public EthDockerRef {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(sha, "sha");
        if (tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        if (sha.isBlank()) {
            throw new IllegalArgumentException("sha must not be blank");
        }
    }
}
