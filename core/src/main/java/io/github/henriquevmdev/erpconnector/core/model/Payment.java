package io.github.henriquevmdev.erpconnector.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record Payment(
        PaymentMethod method,
        Money amount,
        Optional<Integer> installments,
        Optional<Instant> paidAt) {

    public Payment {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(installments, "installments");
        Objects.requireNonNull(paidAt, "paidAt");
    }
}
