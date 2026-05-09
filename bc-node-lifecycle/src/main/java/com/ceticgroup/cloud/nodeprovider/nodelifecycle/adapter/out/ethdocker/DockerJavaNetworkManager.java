package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.ConflictException;
import java.util.Objects;

public class DockerJavaNetworkManager implements DockerNetworkManager {

    private final DockerClient dockerClient;

    public DockerJavaNetworkManager(DockerClient dockerClient) {
        this.dockerClient = Objects.requireNonNull(dockerClient, "dockerClient");
    }

    @Override
    public void ensureSharedNetworkExists(String name) {
        Objects.requireNonNull(name, "name");
        try {
            dockerClient.createNetworkCmd().withName(name).withDriver("bridge").exec();
        } catch (ConflictException ignored) {
            // network already exists - nothing to do
        }
    }
}
