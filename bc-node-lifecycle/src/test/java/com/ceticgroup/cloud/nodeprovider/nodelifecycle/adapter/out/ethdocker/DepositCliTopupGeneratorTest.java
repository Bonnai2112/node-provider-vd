package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.out.ethdocker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DepositCliTopupGeneratorTest {

    // Reference vectors from EIP-55 (https://eips.ethereum.org/EIPS/eip-55).
    @Test
    void toEip55Checksum_should_matchSpecVectors_when_givenLowercaseInput() {
        assertThat(
                        DepositCliTopupGenerator.toEip55Checksum(
                                "5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"))
                .isEqualTo("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
        assertThat(
                        DepositCliTopupGenerator.toEip55Checksum(
                                "fb6916095ca1df60bb79ce92ce3ea74c37c5d359"))
                .isEqualTo("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359");
        assertThat(
                        DepositCliTopupGenerator.toEip55Checksum(
                                "dbf03b407c01e7cd3cbea99509d93f8dddc8c6fb"))
                .isEqualTo("0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB");
        assertThat(
                        DepositCliTopupGenerator.toEip55Checksum(
                                "d1220a0cf47c7b9be7a2e6ba89f429762e7b9adb"))
                .isEqualTo("0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb");
    }

    @Test
    void toEip55Checksum_should_acceptAlreadyMixedCase_when_givenMixedCaseInput() {
        // Idempotent: feeding already-checksummed input must produce the same output.
        String checksummed = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        String stripped = checksummed.substring(2);
        assertThat(DepositCliTopupGenerator.toEip55Checksum(stripped)).isEqualTo(checksummed);
    }
}
