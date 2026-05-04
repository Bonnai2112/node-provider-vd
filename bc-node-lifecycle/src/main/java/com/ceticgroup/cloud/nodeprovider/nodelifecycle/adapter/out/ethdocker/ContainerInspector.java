package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.RuntimeStatus;

public interface ContainerInspector {

    RuntimeStatus inspectByProject(String composeProjectName);
}
