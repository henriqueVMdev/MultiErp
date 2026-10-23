package io.github.henriquevmdev.erpconnector.core.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in a currency. Never {@code double}.
 * ponytail: equality follows {@link BigDecimal#equals}, so 10.0 and 10.00 differ; connectors keep the ERP's scale.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }
}
