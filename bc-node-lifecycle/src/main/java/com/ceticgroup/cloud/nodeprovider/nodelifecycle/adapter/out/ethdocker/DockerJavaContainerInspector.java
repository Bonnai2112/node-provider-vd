package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import java.util.List;
import java.util.Map;

public class DockerJavaContainerInspector implements ContainerInspector {

    private final DockerClient dockerClient;

    public DockerJavaContainerInspector(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public RuntimeStatus inspectByProject(String composeProjectName) {
        try {
            List<Container> containers =
                    dockerClient
                            .listContainersCmd()
                            .withShowAll(true)
                            .withLabelFilter(
                                    Map.of("com.docker.compose.project", composeProjectName))
                            .exec();
            if (containers.isEmpty()) {
                return new RuntimeStatus.Unknown();
            }
            boolean anyExited = false;
            boolean anyRestarting = false;
            String exitedReason = null;
            for (Container c : containers) {
                String state = c.getState() == null ? "" : c.getState();
                switch (state) {
                    case "running":
                        break;
                    case "exited", "dead":
                        anyExited = true;
                        if (exitedReason == null) {
                            exitedReason = "container " + c.getId() + " state=" + state;
                        }
                        break;
                    case "restarting":
                        anyRestarting = true;
                        break;
                    case "created", "paused":
                        anyRestarting = true;
                        break;
                    default:
                        anyRestarting = true;
                }
            }
            if (anyExited) {
                return new RuntimeStatus.Crashed(exitedReason);
            }
            if (anyRestarting) {
                return new RuntimeStatus.Starting();
            }
            return new RuntimeStatus.Running();
        } catch (RuntimeException e) {
            return new RuntimeStatus.Unknown();
        }
    }
}
