package io.github.henriquevmdev.erpconnector.core.connector;

import java.util.Objects;
import java.util.Set;

/**
 * Identifies the ERP and declares what the connector supports. {@code erpId} is the value the connector puts in
 * {@code Order.source()}.
 */
public record ConnectorInfo(String erpId, Set<Capability> capabilities) {

    public enum Capability {
        /** The ERP filters orders by change date, so incremental reads only return changed orders. */
        FILTER_BY_UPDATE_DATE,
        /** Orders carry their change timestamp, so {@code Order.updatedAt()} is filled. */
        UPDATED_AT_IN_PAYLOAD,
        /** The ERP can push change notifications. Receiving them is up to the consumer. */
        WEBHOOKS,
        /** The ERP offers a sandbox or test account. */
        SANDBOX
    }

    public ConnectorInfo {
        Objects.requireNonNull(erpId, "erpId");
        if (erpId.isBlank()) {
            throw new IllegalArgumentException("erpId must not be blank");
        }
        capabilities = Set.copyOf(capabilities);
    }
}
