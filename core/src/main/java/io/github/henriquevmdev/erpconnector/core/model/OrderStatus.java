package io.github.henriquevmdev.erpconnector.core.model;

import java.util.Objects;
import java.util.Optional;

/** Canonical status plus the ERP's original status text. */
public record OrderStatus(Code code, Optional<String> raw) {

    public enum Code {
        PENDING, APPROVED, INVOICED, SHIPPED, DELIVERED, NOT_DELIVERED, RETURNING, CANCELLED, UNKNOWN
    }

    public OrderStatus {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(raw, "raw");
    }
}
