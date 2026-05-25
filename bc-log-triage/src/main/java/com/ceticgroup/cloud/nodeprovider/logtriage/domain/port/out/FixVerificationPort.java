package com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ProposedFix;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.VerificationReport;

public interface FixVerificationPort {

    VerificationReport verify(ProposedFix fix);
}
