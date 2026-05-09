package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

/**
 * Owns the lifecycle of long-lived docker networks shared across compose projects. Today this is
 * just the {@code node-provider-shared} network used for cross-node checkpoint-sync; the
 * abstraction lets us swap implementations (docker-java, shell-out, fake) without leaking docker
 * details into the orchestrator.
 */
public interface DockerNetworkManager {

    void ensureSharedNetworkExists(String name);
}
