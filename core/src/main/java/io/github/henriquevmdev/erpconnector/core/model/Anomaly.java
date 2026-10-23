package io.github.henriquevmdev.erpconnector.core.model;

import java.util.Objects;

/**
 * Something wrong with the ERP data that did not stop the order from being read (AGENTS.md, hard rule 6).
 * {@code detail} must never contain personal data or the offending value itself.
 */
public record Anomaly(Code code, String field, String detail) {

    public enum Code { INVALID_DOCUMENT, TOTAL_MISMATCH, UNKNOWN_STATUS, UNKNOWN_PAYMENT_METHOD, INVALID_VALUE }

    public Anomaly {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(detail, "detail");
    }
}
