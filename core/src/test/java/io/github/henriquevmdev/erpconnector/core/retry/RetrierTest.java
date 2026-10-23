package io.github.henriquevmdev.erpconnector.core.retry;

import io.github.henriquevmdev.erpconnector.core.error.AuthenticationException;
import io.github.henriquevmdev.erpconnector.core.error.ConnectorException;
import io.github.henriquevmdev.erpconnector.core.error.PermanentException;
import io.github.henriquevmdev.erpconnector.core.error.RateLimitedException;
import io.github.henriquevmdev.erpconnector.core.error.TransientException;
import io.github.henriquevmdev.erpconnector.core.error.UnexpectedFormatException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrierTest {

    private static final RetryPolicy FIVE_ATTEMPTS = new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(3));

    private final List<Duration> sleeps = new ArrayList<>();
    private final Sleeper recordingSleeper = sleeps::add;
    /** Always picks the top of the jitter range, so the backoff cap is visible in {@link #sleeps}. */
    private final RandomGenerator maxJitter = new RandomGenerator() {
        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("only bounded draws are expected");
        }

        @Override
        public long nextLong(long origin, long bound) {
            return bound - 1;
        }
    };

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void returnsAtOnceWhenTheFirstCallSucceeds() {
        String result = retrier(FIVE_ATTEMPTS).call(() -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();

        String result = retrier(FIVE_ATTEMPTS).call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientException("timeout");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void stopsAtMaxAttemptsAndThrowsTheLastFailure() {
        AtomicInteger calls = new AtomicInteger();
        List<TransientException> thrown = new ArrayList<>();

        assertThatThrownBy(() -> retrier(FIVE_ATTEMPTS).call(() -> {
            calls.incrementAndGet();
            TransientException e = new TransientException("timeout " + calls.get());
            thrown.add(e);
            throw e;
        })).isSameAs(thrown.getLast());

        assertThat(calls).hasValue(5);
        assertThat(sleeps).hasSize(4);
    }

    @Test
    void backoffDoublesAndIsCappedAtMaxBackoff() {
        assertThatThrownBy(() -> retrier(FIVE_ATTEMPTS).call(() -> {
            throw new TransientException("down");
        })).isInstanceOf(TransientException.class);

        assertThat(sleeps).containsExactly(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    @Test
    void jitterStaysWithinTheBackoffWindow() {
        List<long[]> bounds = new ArrayList<>();
        RandomGenerator recordingRandom = new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long nextLong(long origin, long bound) {
                bounds.add(new long[] {origin, bound});
                return origin;
            }
        };

        assertThatThrownBy(() -> new Retrier(FIVE_ATTEMPTS, recordingSleeper, recordingRandom).call(() -> {
            throw new TransientException("down");
        })).isInstanceOf(TransientException.class);

        assertThat(bounds).extracting(b -> b[0]).containsOnly(0L);
        assertThat(bounds).extracting(b -> b[1]).containsExactly(1001L, 2001L, 3001L, 3001L);
        assertThat(sleeps).containsOnly(Duration.ZERO);
    }

    @ParameterizedTest
    @MethodSource("nonRetryable")
    void neverRetriesNonRetryableFailures(ConnectorException failure) {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier(FIVE_ATTEMPTS).call(() -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    static Stream<ConnectorException> nonRetryable() {
        return Stream.of(
                new AuthenticationException("bad credentials"),
                new PermanentException("not found", OptionalInt.of(404)),
                new UnexpectedFormatException("missing id", Optional.empty(), "id"));
    }

    @Test
    void doesNotRetryExceptionsOutsideTheHierarchy() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier(FIVE_ATTEMPTS).call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bug");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void waitsExactlyWhatTheRateLimitAsks() {
        AtomicInteger calls = new AtomicInteger();

        retrier(FIVE_ATTEMPTS).call(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new RateLimitedException("429", Optional.of(Duration.ofMillis(2500)));
            }
            return "ok";
        });

        assertThat(sleeps).containsExactly(Duration.ofMillis(2500));
    }

    @Test
    void rateLimitWithoutWaitTimeUsesBackoff() {
        AtomicInteger calls = new AtomicInteger();

        retrier(FIVE_ATTEMPTS).call(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new RateLimitedException("429", Optional.empty());
            }
            return "ok";
        });

        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void givesUpAtOnceWhenTheRateLimitAsksMoreThanMaxBackoff() {
        RateLimitedException limited = new RateLimitedException("429", Optional.of(Duration.ofMinutes(10)));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier(FIVE_ATTEMPTS).call(() -> {
            calls.incrementAndGet();
            throw limited;
        })).isSameAs(limited);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void interruptedWaitStopsRetryingAndKeepsTheInterruptFlag() {
        TransientException failure = new TransientException("timeout");
        Sleeper interrupted = d -> {
            throw new InterruptedException();
        };
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> new Retrier(FIVE_ATTEMPTS, interrupted, maxJitter).call(() -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(calls).hasValue(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void largeAttemptCountsDoNotOverflowTheBackoff() {
        RetryPolicy many = new RetryPolicy(80, Duration.ofMillis(1), Duration.ofSeconds(3));

        assertThatThrownBy(() -> retrier(many).call(() -> {
            throw new TransientException("down");
        })).isInstanceOf(TransientException.class);

        // 1 ms doubled reaches the 3 s cap at the 13th wait (2^12 ms > 3 s). From there on, every wait must stay at
        // the cap; Java shifts are taken modulo 64, so a missing overflow guard would drop wait 65 back to 1 ms.
        assertThat(sleeps).hasSize(79);
        assertThat(sleeps.subList(12, 79)).containsOnly(Duration.ofSeconds(3));
    }

    @Test
    void defaultsAreThreeAttemptsFromOneToThirtySeconds() {
        assertThat(RetryPolicy.defaults())
                .isEqualTo(new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30)));
    }

    @Test
    void rejectsPoliciesThatCannotWork() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Retrier retrier(RetryPolicy policy) {
        return new Retrier(policy, recordingSleeper, maxJitter);
    }
}
