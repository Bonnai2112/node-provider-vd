package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import java.net.URI;
import java.util.Optional;

/**
 * Picks a local CL beacon node — already past initial sync — to use as checkpoint-sync source for a
 * freshly-provisioned CL on the same network. The first node bootstraps from a public checkpoint
 * (e.g. https://hoodi.checkpoint.sigp.io); subsequent nodes piggy-back on the already-synced peer
 * over a shared docker network.
 */
public interface CheckpointSyncSourceLocator {

    Optional<URI> findFor(Network network);
}
