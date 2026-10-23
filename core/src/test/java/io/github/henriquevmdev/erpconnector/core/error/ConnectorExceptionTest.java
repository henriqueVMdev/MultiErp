package io.github.henriquevmdev.erpconnector.core.error;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorExceptionTest {

    private static final List<ConnectorException> ALL = List.of(
            new AuthenticationException("bad credentials"),
            new RateLimitedException("429", Optional.of(Duration.ofSeconds(5))),
            new TransientException("timeout"),
            new PermanentException("bad request", OptionalInt.of(400)),
            new UnexpectedFormatException("missing total", Optional.of("1001"), "total"));

    /** No default branch: adding a subtype without handling it here is a compile error. */
    private static String category(ConnectorException e) {
        return switch (e) {
            case AuthenticationException a -> "auth";
            case RateLimitedException r -> "rate-limit";
            case TransientException t -> "transient";
            case PermanentException p -> "permanent";
            case UnexpectedFormatException u -> "format";
        };
    }

    @Test
    void everySubtypeHasACategory() {
        assertThat(ALL).extracting(ConnectorExceptionTest::category)
                .containsExactly("auth", "rate-limit", "transient", "permanent", "format");
    }

    @Test
    void onlyRateLimitsAndTransientFailuresAreRetryable() {
        assertThat(ALL).filteredOn(ConnectorException::retryable)
                .hasExactlyElementsOfTypes(RateLimitedException.class, TransientException.class);
    }

    @Test
    void optionalDetailsSurviveAndCanBeEmpty() {
        assertThat(new RateLimitedException("429", Optional.empty()).retryAfter()).isEmpty();
        assertThat(new PermanentException("sql error", OptionalInt.empty()).httpStatus()).isEmpty();
        assertThat(new PermanentException("bad request", OptionalInt.of(400)).httpStatus()).hasValue(400);
        UnexpectedFormatException format = new UnexpectedFormatException("missing", Optional.of("1001"), "total");
        assertThat(format.orderExternalId()).contains("1001");
        assertThat(format.field()).isEqualTo("total");
    }

    @Test
    void survivesJavaSerialization() throws Exception {
        for (ConnectorException e : ALL) {
            ConnectorException copy = roundTrip(e);
            assertThat(copy).isInstanceOf(e.getClass()).hasMessage(e.getMessage());
        }
        RateLimitedException limited = (RateLimitedException) roundTrip(ALL.get(1));
        assertThat(limited.retryAfter()).contains(Duration.ofSeconds(5));
    }

    private static ConnectorException roundTrip(ConnectorException e) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(e);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ConnectorException) in.readObject();
        }
    }
}
