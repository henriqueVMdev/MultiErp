package io.github.henriquevmdev.erpconnector.core.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Synthetic vectors computed by an independent implementation of the algorithm, not by this class.
 * They are not real documents. {@code 12ABC34501DE35} is the example published by Serpro.
 */
class TaxDocumentsTest {

    @ParameterizedTest
    @ValueSource(strings = {"12345678909", "98765432100", "00000001910", "123.456.789-09"})
    void acceptsValidCpf(String cpf) {
        assertThat(TaxDocuments.isValidCpf(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678000195", "12ABC34501DE35", "ABCDEFGH000195", "12.ABC.345/01DE-35", "12abc34501de35"})
    void acceptsValidCnpj(String cnpj) {
        assertThat(TaxDocuments.isValidCnpj(cnpj)).isTrue();
    }

    @Test
    void rejectsAnyOtherCheckDigit() {
        assertEveryCheckDigitChangeIsRejected("12345678909", false);
        assertEveryCheckDigitChangeIsRejected("98765432100", false);
        assertEveryCheckDigitChangeIsRejected("12ABC34501DE35", true);
        assertEveryCheckDigitChangeIsRejected("12345678000195", true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678900", "12345678919", "98765432101"})
    void rejectsWrongCpfCheckDigits(String cpf) {
        assertThat(TaxDocuments.isValidCpf(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678000194", "12ABC34501DE36", "12ABC34501DF35", "ABCDEFGH000159"})
    void rejectsWrongCnpjCheckDigits(String cnpj) {
        assertThat(TaxDocuments.isValidCnpj(cnpj)).isFalse();
    }

    /**
     * Modulo 11 does not catch every single-character change. These pass in the official algorithm too;
     * a stricter check here would reject documents the Receita considers valid.
     */
    @Test
    void followsTheOfficialAlgorithmEvenWhereItIsBlind() {
        // Remainders 0 and 1 both give check digit 0: 1->2 in the first position goes unnoticed.
        assertThat(TaxDocuments.isValidCpf("22345678909")).isTrue();
        // 'A' is worth 17 and '6' is worth 6: a difference of 11 is invisible to modulo 11.
        assertThat(TaxDocuments.isValidCnpj("126BC34501DE35")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111111", "00000000000", "1234567890", "123456789091", "1234567890A", ""})
    void rejectsMalformedCpf(String cpf) {
        assertThat(TaxDocuments.isValidCpf(cpf)).isFalse();
    }

    @ParameterizedTest
    // Last case: letters are allowed in the first 12 positions only; check digits are always numeric.
    @ValueSource(strings = {"00000000000000", "11111111111111", "1234567800019", "123456780001955", "12ABC34501DE3A", "12ABC34501DE#5"})
    void rejectsMalformedCnpj(String cnpj) {
        assertThat(TaxDocuments.isValidCnpj(cnpj)).isFalse();
    }

    private static void assertEveryCheckDigitChangeIsRejected(String valid, boolean cnpj) {
        for (int i = valid.length() - 2; i < valid.length(); i++) {
            for (char c : "0123456789".toCharArray()) {
                if (c == valid.charAt(i)) {
                    continue;
                }
                String changed = valid.substring(0, i) + c + valid.substring(i + 1);
                boolean result = cnpj ? TaxDocuments.isValidCnpj(changed) : TaxDocuments.isValidCpf(changed);
                assertThat(result).as("changed position %d to '%s': %s", i, c, changed).isFalse();
            }
        }
    }
}
