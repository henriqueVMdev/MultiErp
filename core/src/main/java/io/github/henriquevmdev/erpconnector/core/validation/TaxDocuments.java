package io.github.henriquevmdev.erpconnector.core.validation;

import java.util.Locale;

/**
 * Check-digit validation for Brazilian tax documents (CPF and CNPJ, including the alphanumeric CNPJ).
 *
 * <p>Alphanumeric CNPJ rules follow Nota Técnica Conjunta 2025.001 (Receita Federal / ENCAT): 12 characters
 * {@code [A-Z0-9]} plus 2 numeric check digits; each character is worth its ASCII code minus 48; modulo 11.
 * Numeric CNPJs are a special case of the same algorithm.
 */
public final class TaxDocuments {

    private static final int[] CNPJ_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /** Removes the usual mask characters ({@code . / -} and spaces) and upper-cases letters. */
    public static String normalize(String raw) {
        return raw.replaceAll("[./\\-\\s]", "").toUpperCase(Locale.ROOT);
    }

    /** Accepts masked or unmasked input. */
    public static boolean isValidCpf(String raw) {
        String cpf = normalize(raw);
        if (!cpf.matches("\\d{11}") || allSameChar(cpf)) {
            return false;
        }
        int[] digits = cpf.chars().map(c -> c - '0').toArray();
        return checkDigit(digits, 9) == digits[9] && checkDigit(digits, 10) == digits[10];
    }

    /** Accepts masked or unmasked input, numeric or alphanumeric. */
    public static boolean isValidCnpj(String raw) {
        String cnpj = normalize(raw);
        if (!cnpj.matches("[A-Z0-9]{12}\\d{2}") || allSameChar(cnpj)) {
            return false;
        }
        int[] values = cnpj.chars().map(c -> c - '0').toArray();
        int dv1 = mod11(weightedSum(values, 12, 1));
        int dv2 = mod11(weightedSum(values, 12, 0) + dv1 * CNPJ_WEIGHTS[12]);
        return dv1 == values[12] && dv2 == values[13];
    }

    /** CPF check digit at {@code position} (9 or 10): weights run from {@code position + 1} down to 2. */
    private static int checkDigit(int[] digits, int position) {
        int sum = 0;
        for (int i = 0; i < position; i++) {
            sum += digits[i] * (position + 1 - i);
        }
        return mod11(sum);
    }

    private static int weightedSum(int[] values, int length, int weightOffset) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += values[i] * CNPJ_WEIGHTS[i + weightOffset];
        }
        return sum;
    }

    private static int mod11(int sum) {
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean allSameChar(String s) {
        return s.chars().allMatch(c -> c == s.charAt(0));
    }

    private TaxDocuments() {
    }
}
