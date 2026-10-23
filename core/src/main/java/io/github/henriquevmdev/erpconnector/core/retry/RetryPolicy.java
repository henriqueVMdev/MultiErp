package io.github.henriquevmdev.erpconnector.core.retry;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounded retry budget. {@code maxAttempts} counts the first call: 3 means one call plus at most two retries.
 * Waits grow exponentially from {@code initialBackoff} and never exceed {@code maxBackoff}.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be shorter than initialBackoff");
        }
    }

    /** 3 attempts, waits from 1 s up to 30 s. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30));
    }
}
