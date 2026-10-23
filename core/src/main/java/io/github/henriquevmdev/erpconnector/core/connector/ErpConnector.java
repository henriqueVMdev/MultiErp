package io.github.henriquevmdev.erpconnector.core.connector;

import io.github.henriquevmdev.erpconnector.core.error.ConnectorException;
import io.github.henriquevmdev.erpconnector.core.model.Order;

import java.time.Instant;
import java.util.Optional;

/**
 * Reads sales orders from one ERP account. One instance per account; instances never share state.
 *
 * <p>Reading is incremental and paginated: start with {@link #listOrders(Instant, Instant)}, then keep calling
 * {@link #listOrders(String)} with the returned cursor until {@link OrderPage#nextCursor()} is empty. The caller
 * stores the cursor between runs; it is only valid for the instance's ERP account.
 *
 * <p>Delivery is at least once: the same order can appear more than once, within a read or across reads. Deduplicate
 * by ({@link Order#source()}, {@link Order#externalId()}). ERPs edit orders days after creation, so re-read an overlap
 * window on each sync.
 *
 * <p>Every failure is a {@link ConnectorException} subtype. Bad data in an order is not a failure: the order comes
 * back with anomalies attached.
 */
public interface ErpConnector {

    /** Which ERP this is and what it supports. Never calls the ERP. */
    ConnectorInfo info();

    /** Verifies credentials and permissions; returns normally when the connector can read orders. */
    void checkConnection();

    /**
     * First page of the orders changed in {@code [changedFrom, changedTo)}: start inclusive, end exclusive, so
     * consecutive windows neither overlap nor leave gaps. The caller chooses {@code changedTo} (usually "now"), so the
     * library never reads a clock.
     *
     * @throws IllegalArgumentException if {@code changedFrom} is not before {@code changedTo}
     */
    OrderPage listOrders(Instant changedFrom, Instant changedTo);

    /**
     * Next page of a read started by {@link #listOrders(Instant, Instant)}.
     *
     * @throws IllegalArgumentException if the cursor was not issued by this connector
     */
    OrderPage listOrders(String cursor);

    /** One order by its ERP id; empty when the ERP has no such order. */
    Optional<Order> fetchOrder(String externalId);
}
