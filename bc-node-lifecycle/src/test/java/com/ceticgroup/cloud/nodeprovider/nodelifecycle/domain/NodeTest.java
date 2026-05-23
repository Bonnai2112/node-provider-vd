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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NodeTest {

    private static final NodeId NODE_ID = new NodeId(UUID.randomUUID());
    private static final OwnerId OWNER_ID = new OwnerId(UUID.randomUUID());
    private static final Network NETWORK = Network.HOODI;
    private static final ClientPair CLIENT_PAIR = ClientPair.besuTeku();
    private static final NodeOptions OPTIONS = NodeOptions.defaults();
    private static final Endpoint ENDPOINT = new Endpoint(URI.create("https://rpc.example.com"));
    private static final DeploymentRef DEPLOYMENT_REF = new DeploymentRef("{\"k\":\"v\"}");

    @Nested
    class Request {

        @Test
        void request_should_createNodeInRequestedStatus_when_validInputs() {
            Node node = Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR, OPTIONS);

            assertThat(node.id()).isEqualTo(NODE_ID);
            assertThat(node.owner()).isEqualTo(OWNER_ID);
            assertThat(node.network()).isEqualTo(NETWORK);
            assertThat(node.clientPair()).isEqualTo(CLIENT_PAIR);
            assertThat(node.status()).isInstanceOf(NodeStatus.Requested.class);
        }

        @Test
        void request_should_emitNodeRequestedEvent_when_created() {
            Node node = Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR, OPTIONS);

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
            assertThatThrownBy(() -> Node.request(null, OWNER_ID, NETWORK, CLIENT_PAIR, OPTIONS))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void request_should_throw_when_ownerIsNull() {
            assertThatThrownBy(() -> Node.request(NODE_ID, null, NETWORK, CLIENT_PAIR, OPTIONS))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void request_should_throw_when_networkIsNull() {
            assertThatThrownBy(() -> Node.request(NODE_ID, OWNER_ID, null, CLIENT_PAIR, OPTIONS))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void request_should_throw_when_clientPairIsNull() {
            assertThatThrownBy(() -> Node.request(NODE_ID, OWNER_ID, NETWORK, null, OPTIONS))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class StartProvisioning {

        @Test
        void startProvisioning_should_transitionToProvisioning_when_inRequested() {
            Node node = newRequestedNode();

            node.startProvisioning(DEPLOYMENT_REF);

            assertThat(node.status()).isInstanceOf(NodeStatus.Provisioning.class);
        }

        @Test
        void startProvisioning_should_emitNodeProvisioningStarted_when_inRequested() {
            Node node = newRequestedNode();
            node.pullEvents();

            node.startProvisioning(DEPLOYMENT_REF);

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

            assertThatThrownBy(() -> node.startProvisioning(DEPLOYMENT_REF))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void startProvisioning_should_throw_when_inReady() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.startProvisioning(DEPLOYMENT_REF))
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
    class MarkStopped {

        @Test
        void markStopped_should_transitionFromProvisioningToStopped() {
            Node node = nodeInProvisioning();

            node.markStopped("containers exited");

            assertThat(node.status())
                    .isInstanceOfSatisfying(
                            NodeStatus.Stopped.class,
                            s -> assertThat(s.reason()).isEqualTo("containers exited"));
        }

        @Test
        void markStopped_should_transitionFromSyncingToStopped() {
            Node node = nodeInSyncing();

            node.markStopped("containers exited");

            assertThat(node.status()).isInstanceOf(NodeStatus.Stopped.class);
        }

        @Test
        void markStopped_should_transitionFromReadyToStopped() {
            Node node = nodeInReady();

            node.markStopped("EL=Crashed(oom)");

            assertThat(node.status()).isInstanceOf(NodeStatus.Stopped.class);
        }

        @Test
        void markStopped_should_transitionFromDegradedToStopped() {
            Node node = nodeInDegraded();

            node.markStopped("EL=Crashed(oom)");

            assertThat(node.status()).isInstanceOf(NodeStatus.Stopped.class);
        }

        @Test
        void markStopped_should_notEmitEvent() {
            Node node = nodeInReady();
            node.pullEvents();

            node.markStopped("oops");

            assertThat(node.pullEvents()).isEmpty();
        }

        @Test
        void markStopped_should_throw_when_inRequested() {
            Node node = newRequestedNode();

            assertThatThrownBy(() -> node.markStopped("any"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markStopped_should_throw_when_alreadyStopped() {
            Node node = nodeInStopped();

            assertThatThrownBy(() -> node.markStopped("again"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markStopped_should_throw_when_terminating() {
            Node node = nodeInTerminating();

            assertThatThrownBy(() -> node.markStopped("any"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markStopped_should_throw_when_terminated() {
            Node node = nodeInTerminated();

            assertThatThrownBy(() -> node.markStopped("any"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markStopped_should_throw_when_failed() {
            Node node = nodeInFailed();

            assertThatThrownBy(() -> node.markStopped("any"))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void markStopped_should_throw_when_reasonIsBlank() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.markStopped("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void markStopped_should_throw_when_reasonIsNull() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.markStopped(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Restart {

        @Test
        void restart_should_transitionFromStoppedToProvisioning() {
            Node node = nodeInStopped();

            node.restart();

            assertThat(node.status()).isInstanceOf(NodeStatus.Provisioning.class);
        }

        @Test
        void restart_should_preserveDeploymentRef() {
            Node node = nodeInStopped();
            DeploymentRef refBefore = node.deploymentRef();

            node.restart();

            assertThat(node.deploymentRef()).isEqualTo(refBefore);
        }

        @Test
        void restart_should_emitNodeProvisioningStarted() {
            Node node = nodeInStopped();
            node.pullEvents();

            node.restart();

            assertThat(node.pullEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            NodeProvisioningStarted.class,
                            evt -> assertThat(evt.nodeId()).isEqualTo(NODE_ID));
        }

        @Test
        void restart_should_throw_when_inRequested() {
            Node node = newRequestedNode();

            assertThatThrownBy(node::restart).isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void restart_should_throw_when_inReady() {
            Node node = nodeInReady();

            assertThatThrownBy(node::restart).isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void restart_should_throw_when_failed() {
            Node node = nodeInFailed();

            assertThatThrownBy(node::restart).isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void restart_should_throw_when_terminated() {
            Node node = nodeInTerminated();

            assertThatThrownBy(node::restart).isInstanceOf(IllegalNodeTransitionException.class);
        }
    }

    @Nested
    class EnableValidator {

        private static final String FEE = "0x1111111111111111111111111111111111111111";

        @Test
        void enableValidator_should_setOptionsValidatorTrue_when_inReadyAndValidatorOff() {
            Node node = nodeInReady();

            node.enableValidator(FEE, Optional.of("hello-graffiti"));

            assertThat(node.options().validator()).isTrue();
            assertThat(node.options().feeRecipient()).isEqualTo(FEE);
            assertThat(node.options().graffiti()).contains("hello-graffiti");
        }

        @Test
        void enableValidator_should_preserveOtherOptions_when_alreadyHadMevDefaults() {
            Node node = nodeInReady();

            node.enableValidator(FEE, Optional.empty());

            assertThat(node.options().mevBoost()).isFalse();
            assertThat(node.options().graffiti()).isEmpty();
        }

        @Test
        void enableValidator_should_throw_when_alreadyEnabled() {
            Node node = nodeInReadyWithValidator();

            assertThatThrownBy(() -> node.enableValidator(FEE, Optional.empty()))
                    .isInstanceOf(ValidatorAlreadyEnabledException.class);
        }

        @Test
        void enableValidator_should_throw_when_inSyncing() {
            Node node = nodeInSyncing();

            assertThatThrownBy(() -> node.enableValidator(FEE, Optional.empty()))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void enableValidator_should_throw_when_inDegraded() {
            Node node = nodeInDegraded();

            assertThatThrownBy(() -> node.enableValidator(FEE, Optional.empty()))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void enableValidator_should_throw_when_inStopped() {
            Node node = nodeInStopped();

            assertThatThrownBy(() -> node.enableValidator(FEE, Optional.empty()))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void enableValidator_should_throw_when_feeRecipientIsNull() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.enableValidator(null, Optional.empty()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void enableValidator_should_throw_when_feeRecipientIsBlank() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.enableValidator("  ", Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void enableValidator_should_throw_when_graffitiIsNull() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.enableValidator(FEE, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class DisableValidator {

        @Test
        void disableValidator_should_setOptionsValidatorFalse_when_validatorEnabledAndMevOff() {
            Node node = nodeInReadyWithValidator();

            node.disableValidator();

            assertThat(node.options().validator()).isFalse();
        }

        @Test
        void disableValidator_should_throw_when_validatorNotEnabled() {
            Node node = nodeInReady();

            assertThatThrownBy(node::disableValidator)
                    .isInstanceOf(ValidatorNotEnabledException.class);
        }

        @Test
        void disableValidator_should_throw_when_mevBoostStillEnabled() {
            Node node = nodeInReadyWithValidatorAndMevBoost();

            assertThatThrownBy(node::disableValidator)
                    .isInstanceOf(MevBoostRequiresValidatorException.class);
        }

        @Test
        void disableValidator_should_throw_when_notInReady() {
            Node node = nodeInSyncing();

            assertThatThrownBy(node::disableValidator)
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }
    }

    @Nested
    class EnableMevBoost {

        @Test
        void enableMevBoost_should_setOptionsMevTrue_when_validatorEnabled() {
            Node node = nodeInReadyWithValidator();

            node.enableMevBoost("0.1", 80);

            assertThat(node.options().mevBoost()).isTrue();
            assertThat(node.options().mevMinBid()).contains("0.1");
            assertThat(node.options().mevBuildFactor()).hasValue(80);
        }

        @Test
        void enableMevBoost_should_throw_when_validatorNotEnabled() {
            Node node = nodeInReady();

            assertThatThrownBy(() -> node.enableMevBoost("0.1", 80))
                    .isInstanceOf(MevBoostRequiresValidatorException.class);
        }

        @Test
        void enableMevBoost_should_throw_when_alreadyEnabled() {
            Node node = nodeInReadyWithValidatorAndMevBoost();

            assertThatThrownBy(() -> node.enableMevBoost("0.1", 80))
                    .isInstanceOf(MevBoostAlreadyEnabledException.class);
        }

        @Test
        void enableMevBoost_should_throw_when_notInReady() {
            Node node = nodeInSyncing();

            assertThatThrownBy(() -> node.enableMevBoost("0.1", 80))
                    .isInstanceOf(IllegalNodeTransitionException.class);
        }

        @Test
        void enableMevBoost_should_throw_when_buildFactorOutOfRange() {
            Node node = nodeInReadyWithValidator();

            assertThatThrownBy(() -> node.enableMevBoost("0.1", 101))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class DisableMevBoost {

        @Test
        void disableMevBoost_should_setOptionsMevFalse_when_mevEnabled() {
            Node node = nodeInReadyWithValidatorAndMevBoost();

            node.disableMevBoost();

            assertThat(node.options().mevBoost()).isFalse();
            assertThat(node.options().validator()).isTrue();
        }

        @Test
        void disableMevBoost_should_throw_when_mevNotEnabled() {
            Node node = nodeInReadyWithValidator();

            assertThatThrownBy(node::disableMevBoost)
                    .isInstanceOf(MevBoostNotEnabledException.class);
        }

        @Test
        void disableMevBoost_should_throw_when_notInReady() {
            Node node = nodeInSyncing();

            assertThatThrownBy(node::disableMevBoost)
                    .isInstanceOf(IllegalNodeTransitionException.class);
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
        void terminate_should_transitionToTerminating_when_inStopped() {
            Node node = nodeInStopped();

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
        void terminate_should_remainTerminating_when_alreadyTerminating() {
            Node node = nodeInTerminating();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
        }

        @Test
        void terminate_should_remainTerminated_when_alreadyTerminated() {
            Node node = nodeInTerminated();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminated.class);
        }

        @Test
        void terminate_should_transitionToTerminating_when_failed() {
            Node node = nodeInFailed();

            node.terminate();

            assertThat(node.status()).isInstanceOf(NodeStatus.Terminating.class);
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
        void fail_should_transitionToFailed_when_inStopped() {
            Node node = nodeInStopped();

            node.fail("restart failed");

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
            Node node = Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR, OPTIONS);
            node.startProvisioning(DEPLOYMENT_REF);
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
        return Node.request(NODE_ID, OWNER_ID, NETWORK, CLIENT_PAIR, OPTIONS);
    }

    private static Node nodeInProvisioning() {
        Node n = newRequestedNode();
        n.startProvisioning(DEPLOYMENT_REF);
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

    private static Node nodeInStopped() {
        Node n = nodeInReady();
        n.markStopped("test-stopped");
        return n;
    }

    private static Node nodeInReadyWithValidator() {
        Node n = nodeInReady();
        n.enableValidator(
                "0x2222222222222222222222222222222222222222", Optional.of("test-graffiti"));
        return n;
    }

    private static Node nodeInReadyWithValidatorAndMevBoost() {
        Node n = nodeInReadyWithValidator();
        n.enableMevBoost("0.05", 90);
        return n;
    }
}
