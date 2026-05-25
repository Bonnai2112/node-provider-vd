package com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.LogSnippet;
import java.util.List;

public interface LogRetrievalPort {

    List<LogSnippet> retrieveAround(Incident incident);
}
