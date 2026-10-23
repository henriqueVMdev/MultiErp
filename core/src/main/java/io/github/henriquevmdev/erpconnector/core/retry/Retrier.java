package io.github.henriquevmdev.erpconnector.core.retry;

import io.github.henriquevmdev.erpconnector.core.error.ConnectorException;
import io.github.henriquevmdev.erpconnector.core.error.RateLimitedException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Runs a call and repeats it on retryable failures ({@link ConnectorException#retryable()}) within a
 * {@link RetryPolicy}. When the budget is spent, the last failure is thrown as is. No unbounded loops.
 *
 * <p>Waits use exponential backoff with full jitter: before retry {@code n}, a random wait in
 * {@code [0, min(maxBackoff, initialBackoff * 2^(n-1))]}. A rate limit that says how long to wait is obeyed exactly,
 * unless that is longer than {@code maxBackoff}: then the failure is thrown at once and the caller decides.
 */
public final class Retrier {

    private final RetryPolicy policy;
    private final Sleeper sleeper;
    private final RandomGenerator random;

    public Retrier(RetryPolicy policy, Sleeper sleeper, RandomGenerator random) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Real sleeping and a thread-safe random source. */
    public static Retrier of(RetryPolicy policy) {
        RandomGenerator threadSafe = () -> ThreadLocalRandom.current().nextLong();
        return new Retrier(policy, Sleeper.SYSTEM, threadSafe);
    }

    public <T> T call(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (ConnectorException e) {
                if (!e.retryable() || attempt >= policy.maxAttempts()) {
                    throw e;
                }
                Duration wait = waitBefore(attempt, e);
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private Duration waitBefore(int attempt, ConnectorException failure) {
        if (failure instanceof RateLimitedException limited && limited.retryAfter().isPresent()) {
            Duration asked = limited.retryAfter().get();
            if (asked.compareTo(policy.maxBackoff()) > 0) {
                throw failure;
            }
            return asked;
        }
        long capMillis = backoffCapMillis(attempt);
        return Duration.ofMillis(random.nextLong(0, capMillis + 1));
    }

    /**
     * {@code min(maxBackoff, initialBackoff * 2^(attempt-1))}, without overflowing: when {@code initial} is above
     * {@code max >> exponent}, the doubled value would pass {@code max}, so it is never computed.
     */
    private long backoffCapMillis(int attempt) {
        long max = policy.maxBackoff().toMillis();
        long initial = policy.initialBackoff().toMillis();
        int exponent = attempt - 1;
        if (exponent >= Long.SIZE - 2 || initial > (max >> exponent)) {
            return max;
        }
        return initial << exponent;
    }
}
