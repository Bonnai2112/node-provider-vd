package com.ceticgroup.cloud.nodeprovider.logtriage.domain;

import java.util.Objects;

public record CodeSnippet(String path, int startLine, int endLine, String content) {

    public CodeSnippet {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(content, "content");
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException(
                    "invalid line range: start=" + startLine + " end=" + endLine);
        }
    }
}
