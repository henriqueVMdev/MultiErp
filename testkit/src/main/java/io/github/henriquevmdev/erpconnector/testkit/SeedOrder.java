package io.github.henriquevmdev.erpconnector.testkit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An order the conformance suite asks the connector author to put in the test backend (a stubbed HTTP API, a test
 * database, an in-memory fake), in the ERP's own format. Only what the suite checks is described here; fill every
 * other field the ERP requires with synthetic values.
 *
 * <p>An empty field means the ERP must not provide it at all, so the connector has to report it as unknown.
 *
 * @param externalId      the ERP's id of the order
 * @param changedAt       when the ERP last changed the order; drives the incremental filter
 * @param customerDocument CPF or CNPJ as the ERP would send it
 * @param customerEmail   buyer e-mail
 * @param customerPhone   buyer phone
 * @param channel         the ERP's raw text for the sales channel
 */
public record SeedOrder(
        String externalId,
        Instant changedAt,
        Optional<String> customerDocument,
        Optional<String> customerEmail,
        Optional<String> customerPhone,
        Optional<String> channel) {

    public SeedOrder {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(changedAt, "changedAt");
        Objects.requireNonNull(customerDocument, "customerDocument");
        Objects.requireNonNull(customerEmail, "customerEmail");
        Objects.requireNonNull(customerPhone, "customerPhone");
        Objects.requireNonNull(channel, "channel");
    }
}
