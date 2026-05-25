package com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.in;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.TriageOutcome;

public interface HandleIncidentUseCase {

    TriageOutcome handle(Incident incident);
}
