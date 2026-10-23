package io.github.henriquevmdev.erpconnector.core.error;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** The ERP refused the call because of its request limit (HTTP 429). {@code retryAfter} is set when the ERP says. */
public final class RateLimitedException extends ConnectorException {

    // Fields are nullable, not Optional: exceptions are Serializable and Optional is not.
    private final Duration retryAfter;

    public RateLimitedException(String message, Optional<Duration> retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter").orElse(null);
    }

    public RateLimitedException(String message, Optional<Duration> retryAfter) {
        this(message, retryAfter, null);
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    @Override
    public boolean retryable() {
        return true;
    }
}
