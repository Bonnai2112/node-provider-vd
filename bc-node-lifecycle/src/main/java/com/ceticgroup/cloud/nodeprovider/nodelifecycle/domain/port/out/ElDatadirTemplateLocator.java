package com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ElClient;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.Network;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Locates a pre-built EL datadir tarball for a given (network, el-client) couple. The tarball is
 * produced offline by a "frozen" node and restored into a freshly provisioned datadir to skip the
 * multi-hour from-scratch sync. Absent template = fallback to from-scratch sync.
 */
public interface ElDatadirTemplateLocator {

    Optional<Path> findTemplate(Network network, ElClient el);
}
