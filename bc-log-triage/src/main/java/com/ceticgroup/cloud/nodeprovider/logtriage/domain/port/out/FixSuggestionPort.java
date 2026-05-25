package com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.DiagnosisRequest;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import java.util.Optional;

public interface FixSuggestionPort {

    Optional<ProposedFix> propose(DiagnosisRequest request);
}
