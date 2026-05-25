package com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out;

import com.ceticgroup.cloud.nodeprovider.logtriage.domain.Incident;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.MergeRequestRef;
import com.ceticgroup.cloud.nodeprovider.logtriage.domain.ValidatedFix;

public interface MergeRequestPort {

    /**
     * Ouvre une merge request en draft. L'implémentation DOIT garantir le statut draft (préfixe
     * {@code Draft:} dans le titre côté GitLab) et ne JAMAIS merger automatiquement.
     */
    MergeRequestRef openDraft(ValidatedFix fix, Incident incident);
}
