package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeDomainEvent;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeFailed;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeProvisioningStarted;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeReady;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.event.NodeRequested;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NodeTest {

    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final OwnerId OWNER_ID = new OwnerId(UUID.randomUUID());
    private static final Network NETWORK = Network.HOODI;
    private static final ClientPair CLIENT_PAIR = ClientPair.besuTeku();
    private static final Endpoint ENDPOINT = new Endpoint(URI.create("https://rpc.example.com"));

    @Nested
    class Request {

        @Test
        void request_should_createNodeInRequestedStatus_when_validInputs() {
            Node node = Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR);

            assertThat(node.id()).isEqualTo(NODE_ID);
            assertThat(node.owner()).isEqualTo(OWNER_ID);
            assertThat(node.network()).isEqualTo(NETWORK);
            assertThat(node.clientPair()).isEqualTo(CLIENT_PAIR);
            assertThat(node.status()).isInstanceOf(NodeStatus.Requested.class);
        }

        @Test
        void request_should_emitNodeRequestedEvent_when_created() {
            Node node = Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR);

            List<NodeDomainEvent> events = node.pullEvents();

            assertThat(events)
                    .singleElement()
                    .isInstanceOfSatisfying(
                            NodeRequested.class,
                            evt -> {
                                assertThat(evt.nodeId()).isEqualTo(NODE_ID);
                                assertThat(evt.owner()).isEqualTo(OWNER_ID);
                                assertThat(evt.network()).isEqualTo(NETWORK);
                                assertThat(evt.clientPair()).isEqualTo(CLIENT_PAIR);
                                assertThat(evt.occurredAt()).isNotNull();
                            });
        }

        @Test
        void request_should_throw_when_nodeIdIsNull() {
            assertThatThrownBy(() -> Node.request(null, OWNER_ID, NETWORK, CLIENT_PAIR))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void request_should_throw_when_ownerIsNull() {
            assertThatThrownBy(() -> Node.request(NODE_ID, null, NETWORK, CLIENT_PAIR))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void request_should_throw_when_networkIsNull() {
            assertThatThrownBy(() -> Node.request(NODE_ID, OWNER_ID, null, CLIENT_PAIR))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void request_should_throw_when_clientPairIsNull() {
            assertThatThrownBy(() -> Node.request(NODE_ID, OWNER_ID, NETWORK, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class StartProvisioning {

        @Test
        void startProvisioning_should_transitionToProvisioning_when_inRequested() {
            Node node = newRequestedNode();

            node.startProvisioning();

            assertThat(node.status()).isInstanceOf(NodeStatus.Provisioning.class);
        }

        @Test
        void startProvisioning_should_emitNodeProvisioningStarted_when_inRequested() {
            Node node = newRequestedNode();
            node.pullEvents();

            node.startProvisioning();

            assertThat(node.pullEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            NodeProvisioningStarted.class,
                            evt -> {
                                assertThat(evt.nodeId()).isEqualTo(NODE_ID);
                                assertThat(evt.occurredAt()).isNotNull();
                            });
        }

        @Test
        void startProvisioning_should_throw_when_alreadyProvisioning() {
            Node node = nodeInProvisioning();

            assertThatThrownBy(node::startProvisioning)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void startProvisioning_should_throw_when_inReady() {
            Node node = nodeInReady();

            assertThatThrownBy(node::startProvisioning)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }
    }

    @Nested
    class MarkSyncing {

        @Test
        void markSyncing_should_transitionToSyncing_when_inProvisioning() {
            Node node = nodeInProvisioning();

            node.markSyncing();

            assertThat(node.status()).isInstanceOf(NodeStatus.Syncing.class);
        }

        @Test
        void markSyncing_should_notEmitEvent() {
            Node node = nodeInProvisioning();
            node.pullEvents();

            node.markSyncing();

            assertThat(node.pullEvents()).isEmpty();
        }

        @Test
        void markSyncing_should_throw_when_inRequested() {
            Node node = newRequestedNode();

            assertThatThrownBy(node::markSyncing)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markSyncing_should_throw_when_inReady() {
            Node node = nodeInReady();

            assertThatThrownBy(node::markSyncing)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }
    }

    @Nested
    class MarkReady {

        @Test
        void markReady_should_transitionFromSyncingToReady() {
            Node node = nodeInSyncing();

            node.markReady(ENDPOINT);

            assertThat(node.status())
                    .isInstanceOfSatisfying(
                            NodeStatus.Ready.class,
                            ready -> assertThat(ready.endpoint()).isEqualTo(ENDPOINT));
        }

        @Test
        void markReady_should_transitionFromDegradedToReady_asRecovery() {
            Node node = nodeInDegraded();

            node.markReady(ENDPOINT);

            assertThat(node.status()).isInstanceOf(NodeStatus.Ready.class);
        }

        @Test
        void markReady_should_emitNodeReady_when_transitioningFromSyncing() {
            Node node = nodeInSyncing();
            node.pullEvents();

            node.markReady(ENDPOINT);

            assertThat(node.pullEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            NodeReady.class,
                            evt -> {
                                assertThat(evt.nodeId()).isEqualTo(NODE_ID);
                                assertThat(evt.endpoint()).isEqualTo(ENDPOINT);
                            });
        }

        @Test
        void markReady_should_emitNodeReady_when_recoveringFromDegraded() {
            Node node = nodeInDegraded();
            node.pullEvents();

            node.markReady(ENDPOINT);

            assertThat(node.pullEvents()).singleElement().isInstanceOf(NodeReady.class);
        }

        @Test
        void markReady_should_throw_when_inRequested() {
            Node node = newRequestedNode();

            assertThatThrownBy(() -> node.markReady(ENDPOINT))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markReady_should_throw_when_inProvisioning() {
            Node node = nodeInProvisioning();

            assertThatThrownBy(() -> node.markReady(ENDPOINT))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markReady_should_throw_when_endpointIsNull() {
            Node node = nodeInSyncing();

            assertThatThrownBy(() -> node.markReady(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class MarkDegraded {

        @Test
        void markDegraded_should_transitionFromReadyToDegraded() {
            Node node = nodeInReady();

            node.markDegraded("rpc returned 503");

            assertThat(node.status())
                    .isInstanceOfSatisfying(
                            NodeStatus.Degraded.class,
                            d -> assertThat(d.reason()).isEqualTo("rpc returned 503"));
        }

        @Test
        void markDegraded_should_notEmitEvent() {
            Node node = nodeInReady();
            node.pullEvents();

            node.markDegraded("oops");

            assertThat(node.pullEvents()).isEmpty();
        }

        @Test
        void markDegraded_should_throw_when_inSyncing() {
            Node node = nodeInSyncing();

            assertThatThrownBy(() -> node.markDegraded("any"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markDegraded_should_throw_when_reasonIsBlank() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.markDegraded("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void markDegraded_should_throw_when_reasonIsNull() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.markDegraded(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Terminate {

        @Test
        void terminate_should_transitionToTerminating_when_inRequested() {
            Node node = newRequestedNode();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
        }

        @Test
        void terminate_should_transitionToTerminating_when_inProvisioning() {
            Node node = nodeInProvisioning();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
        }

        @Test
        void terminate_should_transitionToTerminating_when_inSyncing() {
            Node node = nodeInSyncing();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
        }

        @Test
        void terminate_should_transitionToTerminating_when_inReady() {
            Node node = nodeInReady();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
        }

        @Test
        void terminate_should_transitionToTerminating_when_inDegraded() {
            Node node = nodeInDegraded();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
        }

        @Test
        void terminate_should_notEmitEvent() {
            Node node = nodeInReady();
            node.pullEvents();

            node.terminate();

            assertThat(node.pullEvents()).isEmpty();
        }

        @Test
        void terminate_should_throw_when_alreadyTerminating() {
            Node node = nodeInTerminating();

            assertThatThrownBy(node::terminate).isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void terminate_should_throw_when_alreadyTerminated() {
            Node node = nodeInTerminated();

            assertThatThrownBy(node::terminate).isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void terminate_should_throw_when_failed() {
            Node node = nodeInFailed();

            assertThatThrownBy(node::terminate).isInstanceOf(IllegalNodeTransitionException.class);
        }
    }

    @Nested
    class MarkTerminated {

        @Test
        void markTerminated_should_transitionFromTerminatingToTerminated() {
            Node node = nodeInTerminating();

            node.markTerminated();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminated.class);
        }

        @Test
        void markTerminated_should_notEmitEvent() {
            Node node = nodeInTerminating();
            node.pullEvents();

            node.markTerminated();

            assertThat(node.pullEvents()).isEmpty();
        }

        @Test
        void markTerminated_should_throw_when_inReady() {
            Node node = nodeInReady();

            assertThatThrownBy(node::markTerminated)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markTerminated_should_throw_when_alreadyTerminated() {
            Node node = nodeInTerminated();

            assertThatThrownBy(node::markTerminated)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }
    }

    @Nested
    class Fail {

        @Test
        void fail_should_transitionToFailed_when_inProvisioning() {
            Node node = nodeInProvisioning();

            node.fail("docker pull timeout");

            assertThat(node.status())
                    .isInstanceOfSatisfying(
                            NodeStatus.Failed.class,
                            f -> assertThat(f.reason()).isEqualTo("docker pull timeout"));
        }

        @Test
        void fail_should_transitionToFailed_when_inSyncing() {
            Node node = nodeInSyncing();

            node.fail("any reason");

            assertThat(node.status()).isInstanceOf(NodeStatus.Failed.class);
        }

        @Test
        void fail_should_transitionToFailed_when_inTerminating() {
            Node node = nodeInTerminating();

            node.fail("cleanup error");

            assertThat(node.status()).isInstanceOf(NodeStatus.Failed.class);
        }

        @Test
        void fail_should_emitNodeFailedEvent() {
            Node node = nodeInProvisioning();
            node.pullEvents();

            node.fail("boom");

            assertThat(node.pullEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            NodeFailed.class,
                            evt -> {
                                assertThat(evt.nodeId()).isEqualTo(NODE_ID);
                                assertThat(evt.reason()).isEqualTo("boom");
                                assertThat(evt.occurredAt()).isNotNull();
                            });
        }

        @Test
        void fail_should_throw_when_alreadyFailed() {
            Node node = nodeInFailed();

            assertThatThrownBy(() -> node.fail("again"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void fail_should_throw_when_terminated() {
            Node node = nodeInTerminated();

            assertThatThrownBy(() -> node.fail("late"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void fail_should_throw_when_reasonIsBlank() {
            Node node = nodeInProvisioning();

            assertThatThrownBy(() -> node.fail("")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class PullEvents {

        @Test
        void pullEvents_should_clearPendingEventsAfterReturn() {
            Node node = newRequestedNode();

            assertThat(node.pullEvents()).hasSize(1);
            assertThat(node.pullEvents()).isEmpty();
        }

        @Test
        void pullEvents_should_returnEventsInOrder_alongFullHappyPath() {
            Node node = Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR);
            node.startProvisioning();
            node.markSyncing();
            node.markReady(ENDPOINT);
            node.markDegraded("flaky");
            node.markReady(ENDPOINT);

            assertThat(node.pullEvents())
                    .extracting(evt -> evt.getClass().getSimpleName())
                    .containsExactly(
                            "NodeRequested", "NodeProvisioningStarted", "NodeReady", "NodeReady");
        }
    }

    private static Node newRequestedNode() {
        return Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR);
    }

    private static Node nodeInProvisioning() {
        Node n = newRequestedNode();
        n.startProvisioning();
        return n;
    }

    private static Node nodeInSyncing() {
        Node n = nodeInProvisioning();
        n.markSyncing();
        return n;
    }

    private static Node nodeInReady() {
        Node n = nodeInSyncing();
        n.markReady(ENDPOINT);
        return n;
    }

    private static Node nodeInDegraded() {
        Node n = nodeInReady();
        n.markDegraded("test-degraded");
        return n;
    }

    private static Node nodeInTerminating() {
        Node n = nodeInReady();
        n.terminate();
        return n;
    }

    private static Node nodeInTerminated() {
        Node n = nodeInTerminating();
        n.markTerminated();
        return n;
    }

    private static Node nodeInFailed() {
        Node n = nodeInProvisioning();
        n.fail("test-failed");
        return n;
    }
}
