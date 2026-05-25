package com.ceticgroup.cloud.nodeprovider.logtriage.adapter.out.gitlab;

public class GitLabMergeRequestException extends RuntimeException {

    public GitLabMergeRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
