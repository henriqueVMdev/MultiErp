package io.github.henriquevmdev.erpconnector.core.error;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A failure that repeating will not fix: other HTTP 4xx, a missing table or column, an invalid query. Never retried.
 * {@code httpStatus} is empty for connectors that do not talk HTTP (for example, JDBC).
 */
public final class PermanentException extends ConnectorException {

    // Nullable, not OptionalInt: exceptions are Serializable and OptionalInt is not.
    private final Integer httpStatus;

    public PermanentException(String message, OptionalInt httpStatus, Throwable cause) {
        super(message, cause);
        Objects.requireNonNull(httpStatus, "httpStatus");
        this.httpStatus = httpStatus.isPresent() ? httpStatus.getAsInt() : null;
    }

    public PermanentException(String message, OptionalInt httpStatus) {
        this(message, httpStatus, null);
    }

    public OptionalInt httpStatus() {
        return httpStatus == null ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }

    @Override
    public boolean retryable() {
        return false;
    }
}
