package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.net.URI;
import java.util.Objects;

public record MergeRequestRef(String projectPath, long iid, URI webUrl) {

    public MergeRequestRef {
        Objects.requireNonNull(projectPath, "projectPath");
        Objects.requireNonNull(webUrl, "webUrl");
        if (iid <= 0) {
            throw new IllegalArgumentException("iid must be positive");
        }
    }
}
