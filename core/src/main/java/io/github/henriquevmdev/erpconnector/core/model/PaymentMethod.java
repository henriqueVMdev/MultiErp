package io.github.henriquevmdev.erpconnector.core.model;

import java.util.Objects;
import java.util.Optional;

/** Canonical payment method plus the ERP's original text. */
public record PaymentMethod(Type type, Optional<String> raw) {

    public enum Type { CREDIT_CARD, DEBIT_CARD, PIX, BOLETO, CASH, BANK_TRANSFER, OTHER, UNKNOWN }

    public PaymentMethod {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(raw, "raw");
    }
}
