package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DockerJavaNetworkManagerTest {

    @Mock private DockerClient dockerClient;

    private CreateNetworkCmd createCmd;

    @BeforeEach
    void setUp() {
        createCmd = mock(CreateNetworkCmd.class);
        when(dockerClient.createNetworkCmd()).thenReturn(createCmd);
        when(createCmd.withName(eq("node-provider-shared"))).thenReturn(createCmd);
        when(createCmd.withDriver(eq("bridge"))).thenReturn(createCmd);
    }

    @Test
    void ensureSharedNetworkExists_should_createNetwork_when_notExisting() {
        when(createCmd.exec()).thenReturn(new CreateNetworkResponse());
        DockerJavaNetworkManager manager = new DockerJavaNetworkManager(dockerClient);

        manager.ensureSharedNetworkExists("node-provider-shared");

        verify(createCmd, times(1)).exec();
    }

    @Test
    void ensureSharedNetworkExists_should_swallowConflictException_when_alreadyExists() {
        when(createCmd.exec()).thenThrow(new ConflictException("already exists"));
        DockerJavaNetworkManager manager = new DockerJavaNetworkManager(dockerClient);

        manager.ensureSharedNetworkExists("node-provider-shared");

        verify(createCmd, times(1)).exec();
        verify(dockerClient, never()).removeNetworkCmd("node-provider-shared");
    }
}
