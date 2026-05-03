package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ClientPair;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Endpoint;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Node;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeStatus;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.OwnerId;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out.NodeRepository;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest
class JpaNodeRepositoryIT {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(1)));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private NodeRepository repository;

    @Autowired private SpringDataNodeJpaRepository jpaRepository;

    @AfterEach
    void cleanup() {
        jpaRepository.deleteAll();
    }

    @Test
    void save_then_findById_should_returnSameAggregate_when_inRequestedStatus() {
        Node node = newRequestedNode();

        repository.save(node);

        Optional<Node> loaded = repository.findById(node.id());
        assertThat(loaded).isPresent();
        Node found = loaded.orElseThrow();
        assertThat(found.id()).isEqualTo(node.id());
        assertThat(found.owner()).isEqualTo(node.owner());
        assertThat(found.network()).isEqualTo(Network.HOODI);
        assertThat(found.clientPair()).isEqualTo(ClientPair.besuTeku());
        assertThat(found.status()).isInstanceOf(NodeStatus.Requested.class);
    }

    @Test
    void save_should_persistReadyStatusAndEndpoint() {
        NodeId id = new NodeId(UUID.randomUUID());
        Node node =
                Node.restore(
                        id,
                        new OwnerId(UUID.randomUUID()),
                        Network.SEPOLIA,
                        ClientPair.besuTeku(),
                        new NodeStatus.Ready(new Endpoint(URI.create("https://rpc.example.com"))));

        repository.save(node);

        Node loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Ready.class,
                        ready ->
                                assertThat(ready.endpoint().uri().toString())
                                        .isEqualTo("https://rpc.example.com"));
    }

    @Test
    void save_should_persistFailedStatusWithReason() {
        NodeId id = new NodeId(UUID.randomUUID());
        Node node =
                Node.restore(
                        id,
                        new OwnerId(UUID.randomUUID()),
                        Network.HOODI,
                        ClientPair.besuTeku(),
                        new NodeStatus.Failed("docker pull timeout"));

        repository.save(node);

        Node loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.status())
                .isInstanceOfSatisfying(
                        NodeStatus.Failed.class,
                        f -> assertThat(f.reason()).isEqualTo("docker pull timeout"));
    }

    @Test
    void findById_should_returnEmpty_when_notFound() {
        Optional<Node> found = repository.findById(new NodeId(UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    @Test
    void findByOwner_should_returnAllNodesForGivenOwner() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        OwnerId otherOwner = new OwnerId(UUID.randomUUID());

        repository.save(
                Node.request(
                        new NodeId(UUID.randomUUID()),
                        owner,
                        Network.HOODI,
                        ClientPair.besuTeku()));
        repository.save(
                Node.request(
                        new NodeId(UUID.randomUUID()),
                        owner,
                        Network.SEPOLIA,
                        ClientPair.besuTeku()));
        repository.save(
                Node.request(
                        new NodeId(UUID.randomUUID()),
                        otherOwner,
                        Network.HOODI,
                        ClientPair.besuTeku()));

        List<Node> result = repository.findByOwner(owner);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.owner().equals(owner));
    }

    @Test
    void findByOwner_should_returnEmpty_when_noNodes() {
        List<Node> result = repository.findByOwner(new OwnerId(UUID.randomUUID()));

        assertThat(result).isEmpty();
    }

    private static Node newRequestedNode() {
        return Node.request(
                new NodeId(UUID.randomUUID()),
                new OwnerId(UUID.randomUUID()),
                Network.HOODI,
                ClientPair.besuTeku());
    }
}
