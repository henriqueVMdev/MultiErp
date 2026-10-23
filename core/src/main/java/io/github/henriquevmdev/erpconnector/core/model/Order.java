package io.github.henriquevmdev.erpconnector.core.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A sales order in the canonical model.
 *
 * <p>{@code source} and {@code externalId} identify the order across reads. {@code number} is the order number shown
 * in the ERP; {@code channelOrderNumber} is the number of the same order in the sales channel (e.g. the marketplace),
 * when the ERP gives it. {@code createdAt} and {@code updatedAt}
 * are filled only when the ERP gives a time and a zone; Bling and Tiny give neither (see
 * {@code docs/research/erp-comparison.md}). Lists are immutable copies.
 */
public record Order(
        String source,
        String externalId,
        Optional<String> number,
        Optional<String> channelOrderNumber,
        Optional<LocalDate> createdDate,
        Optional<Instant> createdAt,
        Optional<Instant> updatedAt,
        OrderStatus status,
        SalesChannel channel,
        Optional<Customer> customer,
        List<Item> items,
        List<Payment> payments,
        Optional<Money> shipping,
        Optional<Money> discount,
        Optional<Money> total,
        Optional<String> invoiceAccessKey,
        List<Anomaly> anomalies) {

    public Order {
        requireNotBlank(source, "source");
        requireNotBlank(externalId, "externalId");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(channelOrderNumber, "channelOrderNumber");
        Objects.requireNonNull(createdDate, "createdDate");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(customer, "customer");
        items = List.copyOf(items);
        payments = List.copyOf(payments);
        Objects.requireNonNull(shipping, "shipping");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(invoiceAccessKey, "invoiceAccessKey");
        anomalies = List.copyOf(anomalies);
    }

    private static void requireNotBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
