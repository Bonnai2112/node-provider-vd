package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

public record DeploymentPayload(
        String workdir, String composeProjectName, AllocatedPorts ports, EthDockerRef ref) {}
