package io.github.henriquevmdev.erpconnector.core.connector;

import io.github.henriquevmdev.erpconnector.core.model.Order;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of orders. {@code nextCursor} is opaque: store it as is and pass it back to
 * {@link ErpConnector#listOrders(String)}. Empty means the read is complete. A page may be empty and still have a
 * next cursor (for example, when the connector splits a long window).
 */
public record OrderPage(List<Order> orders, Optional<String> nextCursor) {

    public OrderPage {
        orders = List.copyOf(orders);
        Objects.requireNonNull(nextCursor, "nextCursor");
    }
}
