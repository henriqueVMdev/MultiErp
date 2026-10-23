package io.github.henriquevmdev.erpconnector.core.error;

import java.util.Objects;
import java.util.Optional;

/**
 * The ERP answered, but in a shape that makes an order unreadable: a required field is missing or has another type.
 * Says which order (when known) and which field, never the field's value. Never retried.
 */
public final class UnexpectedFormatException extends ConnectorException {

    // Nullable, not Optional: exceptions are Serializable and Optional is not.
    private final String orderExternalId;
    private final String field;

    public UnexpectedFormatException(String message, Optional<String> orderExternalId, String field, Throwable cause) {
        super(message, cause);
        this.orderExternalId = Objects.requireNonNull(orderExternalId, "orderExternalId").orElse(null);
        this.field = Objects.requireNonNull(field, "field");
    }

    public UnexpectedFormatException(String message, Optional<String> orderExternalId, String field) {
        this(message, orderExternalId, field, null);
    }

    public Optional<String> orderExternalId() {
        return Optional.ofNullable(orderExternalId);
    }

    public String field() {
        return field;
    }

    @Override
    public boolean retryable() {
        return false;
    }
}
