package io.github.henriquevmdev.erpconnector.core.model;

/** Masks personal data for {@code toString()}. Package-private: the model is the only user. */
final class Masking {

    /** Keeps the last 2 characters: {@code 12345678909} becomes {@code *********09}. */
    static String keepLastTwo(String value) {
        if (value.length() <= 2) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - 2) + value.substring(value.length() - 2);
    }

    /** Keeps the first character of the local part and the domain: {@code a***@example.com}. */
    static String email(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return keepLastTwo(email);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private Masking() {
    }
}
