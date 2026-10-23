package io.github.henriquevmdev.erpconnector.core.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** One order line. {@code discount} and {@code total} stay empty when the ERP does not give them. */
public record Item(
        Optional<String> sku,
        Optional<String> description,
        BigDecimal quantity,
        Money unitPrice,
        Optional<Money> discount,
        Optional<Money> total) {

    public Item {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(total, "total");
    }
}
