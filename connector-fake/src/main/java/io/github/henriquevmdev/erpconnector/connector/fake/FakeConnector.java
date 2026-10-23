package io.github.henriquevmdev.erpconnector.connector.fake;

import io.github.henriquevmdev.erpconnector.core.connector.ConnectorInfo;
import io.github.henriquevmdev.erpconnector.core.connector.ErpConnector;
import io.github.henriquevmdev.erpconnector.core.connector.OrderPage;
import io.github.henriquevmdev.erpconnector.core.error.AuthenticationException;
import io.github.henriquevmdev.erpconnector.core.error.ConnectorException;
import io.github.henriquevmdev.erpconnector.core.error.PermanentException;
import io.github.henriquevmdev.erpconnector.core.error.RateLimitedException;
import io.github.henriquevmdev.erpconnector.core.error.TransientException;
import io.github.henriquevmdev.erpconnector.core.error.UnexpectedFormatException;
import io.github.henriquevmdev.erpconnector.core.model.Anomaly;
import io.github.henriquevmdev.erpconnector.core.model.Customer;
import io.github.henriquevmdev.erpconnector.core.model.Order;
import io.github.henriquevmdev.erpconnector.core.model.OrderStatus;
import io.github.henriquevmdev.erpconnector.core.model.SalesChannel;
import io.github.henriquevmdev.erpconnector.core.model.TaxDocument;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reference connector over {@link FakeErp}. It shows the mapping every real connector does: foreign record to
 * canonical order, foreign errors to {@link ConnectorException} subtypes, and an opaque cursor. Not published.
 */
public final class FakeConnector implements ErpConnector {

    public static final String ERP_ID = "fake";
    private static final String CURSOR_PREFIX = "fake1:";

    private final FakeErp erp;
    private final int pageSize;

    public FakeConnector(FakeErp erp, int pageSize) {
        this.erp = Objects.requireNonNull(erp, "erp");
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        this.pageSize = pageSize;
    }

    @Override
    public ConnectorInfo info() {
        return new ConnectorInfo(ERP_ID, EnumSet.of(
                ConnectorInfo.Capability.FILTER_BY_UPDATE_DATE, ConnectorInfo.Capability.UPDATED_AT_IN_PAYLOAD));
    }

    @Override
    public void checkConnection() {
        try {
            erp.ping();
        } catch (FakeErp.ErpFailure e) {
            throw translate(e);
        }
    }

    @Override
    public OrderPage listOrders(Instant changedFrom, Instant changedTo) {
        if (!changedFrom.isBefore(changedTo)) {
            throw new IllegalArgumentException("changedFrom must be before changedTo");
        }
        return page(changedFrom, changedTo, 0);
    }

    @Override
    public OrderPage listOrders(String cursor) {
        String[] parts = cursor.startsWith(CURSOR_PREFIX) ? cursor.substring(CURSOR_PREFIX.length()).split(":") : null;
        if (parts == null || parts.length != 3) {
            throw new IllegalArgumentException("cursor was not issued by this connector");
        }
        try {
            return page(Instant.ofEpochMilli(Long.parseLong(parts[0])), Instant.ofEpochMilli(Long.parseLong(parts[1])),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cursor was not issued by this connector");
        }
    }

    @Override
    public Optional<Order> fetchOrder(String externalId) {
        try {
            return erp.find(externalId).map(FakeConnector::toOrder);
        } catch (FakeErp.ErpFailure e) {
            throw translate(e);
        }
    }

    private OrderPage page(Instant from, Instant to, int offset) {
        List<FakeErp.Row> rows;
        try {
            // One extra row tells whether another page exists.
            rows = erp.query(from, to, offset, pageSize + 1);
        } catch (FakeErp.ErpFailure e) {
            throw translate(e);
        }
        boolean more = rows.size() > pageSize;
        List<Order> orders = rows.stream().limit(pageSize).map(FakeConnector::toOrder).toList();
        Optional<String> next = more
                ? Optional.of(CURSOR_PREFIX + from.toEpochMilli() + ":" + to.toEpochMilli() + ":" + (offset + pageSize))
                : Optional.empty();
        return new OrderPage(orders, next);
    }

    private static Order toOrder(FakeErp.Row row) {
        if (row.changedAt() == null) {
            throw new UnexpectedFormatException("order has no change date", Optional.of(row.id()), "changedAt");
        }
        List<Anomaly> anomalies = new ArrayList<>();
        Optional<TaxDocument> document = TaxDocument.parse(row.customerDocument());
        if (document.isPresent() && !document.get().checkDigitsValid()) {
            anomalies.add(new Anomaly(Anomaly.Code.INVALID_DOCUMENT, "customer.document", "check digits do not match"));
        }
        Optional<Customer> customer = row.customerDocument() == null && row.customerEmail() == null
                && row.customerPhone() == null
                ? Optional.empty()
                : Optional.of(new Customer(document, Optional.empty(), Optional.ofNullable(row.customerEmail()),
                        Optional.ofNullable(row.customerPhone()), Optional.empty(), Optional.empty(), Optional.empty()));
        return new Order(ERP_ID, row.id(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(row.changedAt()), new OrderStatus(OrderStatus.Code.UNKNOWN, Optional.empty()),
                channel(row.channel()), customer, List.of(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), anomalies);
    }

    private static SalesChannel channel(String raw) {
        if (raw == null) {
            return new SalesChannel(SalesChannel.Type.UNKNOWN, Optional.empty());
        }
        SalesChannel.Type type = switch (raw) {
            case "DIRECT" -> SalesChannel.Type.DIRECT;
            case "MARKETPLACE" -> SalesChannel.Type.MARKETPLACE;
            default -> SalesChannel.Type.UNKNOWN;
        };
        return new SalesChannel(type, Optional.of(raw));
    }

    private static ConnectorException translate(FakeErp.ErpFailure e) {
        return switch (e.problem()) {
            case REJECT_CREDENTIALS -> new AuthenticationException("credentials rejected", e);
            case TOO_MANY_REQUESTS -> new RateLimitedException("request limit reached",
                    Optional.ofNullable(e.retryAfter()), e);
            case UNAVAILABLE -> new TransientException("ERP unavailable", e);
            case BAD_REQUEST -> new PermanentException("request rejected", OptionalInt.empty(), e);
            case CORRUPT_ROW -> throw new IllegalStateException("FakeErp never throws CORRUPT_ROW", e);
        };
    }
}
