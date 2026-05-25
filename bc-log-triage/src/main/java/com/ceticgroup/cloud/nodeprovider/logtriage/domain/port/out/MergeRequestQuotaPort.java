package com.ceticgroup.cloud.nodeprovider.logtriage.domain.port.out;

public interface MergeRequestQuotaPort {

    /**
     * Tente d'atomiquement réserver une place dans le quota journalier de MR. Retourne true si la
     * réservation a réussi, false si le quota est épuisé. Ne libère JAMAIS la place : si la MR
     * échoue à s'ouvrir, la place reste consommée (préférable à un double-spend).
     */
    boolean tryReserve();

    int dailyLimit();
}
