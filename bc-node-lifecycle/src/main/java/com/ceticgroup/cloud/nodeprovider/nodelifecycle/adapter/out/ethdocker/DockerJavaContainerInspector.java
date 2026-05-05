package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.LayerState;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DockerJavaContainerInspector implements ContainerInspector {

    // eth-docker uses these compose service names for the EL beacon node, the CL beacon node
    // and the (optional) validator client. Other services (lighthouse-builder, set-prune-marker,
    // mev-boost, etc.) are one-shot helpers or sidecars and are ignored here.
    private static final String SERVICE_LABEL = "com.docker.compose.service";
    private static final String EL_SERVICE = "execution";
    private static final String CL_SERVICE = "consensus";
    private static final String VALIDATOR_SERVICE = "validator";

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
            LayerState el = layerStateFor(containers, EL_SERVICE);
            LayerState cl = layerStateFor(containers, CL_SERVICE);
            Optional<LayerState> validator = optionalLayerStateFor(containers, VALIDATOR_SERVICE);
            return new RuntimeStatus.Healthy(el, cl, validator);
        } catch (RuntimeException e) {
            return new RuntimeStatus.Unknown();
        }
    }

    private static LayerState layerStateFor(List<Container> containers, String service) {
        Container match = findByService(containers, service);
        if (match == null) {
            return new LayerState.Absent();
        }
        return mapState(match);
    }

    private static Optional<LayerState> optionalLayerStateFor(
            List<Container> containers, String service) {
        Container match = findByService(containers, service);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(mapState(match));
    }

    private static Container findByService(List<Container> containers, String service) {
        for (Container c : containers) {
            Map<String, String> labels = c.getLabels();
            if (labels != null && service.equals(labels.get(SERVICE_LABEL))) {
                return c;
            }
        }
        return null;
    }

    private static LayerState mapState(Container c) {
        String state = c.getState() == null ? "" : c.getState();
        return switch (state) {
            case "running" -> new LayerState.Running();
            case "restarting", "created", "paused" -> new LayerState.Starting();
            case "exited", "dead" ->
                    new LayerState.Crashed("container " + c.getId() + " state=" + state);
            default -> new LayerState.Starting();
        };
    }
}
