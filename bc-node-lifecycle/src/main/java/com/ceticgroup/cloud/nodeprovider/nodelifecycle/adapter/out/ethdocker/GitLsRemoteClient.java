package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import java.util.Optional;

public interface GitLsRemoteClient {

    Optional<String> resolveSha(String repoUrl, String ref);
}
