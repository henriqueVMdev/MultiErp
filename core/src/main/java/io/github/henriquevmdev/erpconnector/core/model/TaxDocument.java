package io.github.henriquevmdev.erpconnector.core.model;

import io.github.henriquevmdev.erpconnector.core.validation.TaxDocuments;

import java.util.Objects;
import java.util.Optional;

/**
 * A customer's tax document. {@code value} is normalized: no mask characters, letters upper-cased.
 * {@code toString()} masks the value.
 */
public record TaxDocument(Type type, String value) {

    public enum Type { CPF, CNPJ, OTHER }

    public TaxDocument {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        boolean shapeMatches = switch (type) {
            case CPF -> value.matches("\\d{11}");
            case CNPJ -> value.matches("[A-Z0-9]{14}");
            case OTHER -> true;
        };
        if (!shapeMatches) {
            throw new IllegalArgumentException("value does not have the shape of a " + type);
        }
    }

    /**
     * Classifies raw ERP text: 11 digits is a CPF, 14 alphanumeric characters is a CNPJ, anything else is OTHER.
     * Blank or null input means the ERP gave no document.
     * ponytail: no zero-padding of short numbers; a CPF that lost its leading zeros becomes OTHER, add padding if an ERP needs it.
     */
    public static Optional<TaxDocument> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = TaxDocuments.normalize(raw);
        if (normalized.matches("\\d{11}")) {
            return Optional.of(new TaxDocument(Type.CPF, normalized));
        }
        if (normalized.matches("[A-Z0-9]{14}")) {
            return Optional.of(new TaxDocument(Type.CNPJ, normalized));
        }
        return Optional.of(new TaxDocument(Type.OTHER, raw.strip()));
    }

    /** Whether the check digits are correct. Always {@code false} for {@link Type#OTHER}. */
    public boolean checkDigitsValid() {
        return switch (type) {
            case CPF -> TaxDocuments.isValidCpf(value);
            case CNPJ -> TaxDocuments.isValidCnpj(value);
            case OTHER -> false;
        };
    }

    @Override
    public String toString() {
        return "TaxDocument[type=" + type + ", value=" + Masking.keepLastTwo(value) + "]";
    }
}
