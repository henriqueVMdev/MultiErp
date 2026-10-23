package io.github.henriquevmdev.erpconnector.testkit;

import io.github.henriquevmdev.erpconnector.core.connector.ConnectorInfo;
import io.github.henriquevmdev.erpconnector.core.connector.ErpConnector;
import io.github.henriquevmdev.erpconnector.core.connector.OrderPage;
import io.github.henriquevmdev.erpconnector.core.error.AuthenticationException;
import io.github.henriquevmdev.erpconnector.core.error.ConnectorException;
import io.github.henriquevmdev.erpconnector.core.error.PermanentException;
import io.github.henriquevmdev.erpconnector.core.error.RateLimitedException;
import io.github.henriquevmdev.erpconnector.core.error.TransientException;
import io.github.henriquevmdev.erpconnector.core.error.UnexpectedFormatException;
import io.github.henriquevmdev.erpconnector.core.model.Anomaly;
import io.github.henriquevmdev.erpconnector.core.model.Customer;
import io.github.henriquevmdev.erpconnector.core.model.Order;
import io.github.henriquevmdev.erpconnector.core.model.SalesChannel;
import io.github.henriquevmdev.erpconnector.core.model.TaxDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Conformance suite every connector must pass. Extend it in the connector's tests and implement the hooks; JUnit runs
 * the inherited tests.
 *
 * <p>What the connector author must provide:
 * <ul>
 *   <li>{@link #connectorWith(List)}: a connector reading from a test backend that holds exactly the given orders, with
 *       a <b>page size of 10 or less</b>, so the suite sees several pages;</li>
 *   <li>{@link #connectorFailingWith(Failure, SeedOrder)}: a connector whose backend fails in the given way while it
 *       holds the given order; configure retries without real sleeping;</li>
 *   <li>optionally {@link #applicableFailures()} and {@link #secrets()}.</li>
 * </ul>
 *
 * <p>Not covered here: what the connector writes to logs. The library itself does not log.
 */
public abstract class ConnectorConformanceSuite {

    /** Ways a backend fails. Each must surface as one exception type. */
    public enum Failure {
        /** Credentials rejected (HTTP 401/403, database login failure). Expect {@link AuthenticationException}. */
        AUTHENTICATION,
        /** Request limit reached (HTTP 429). Expect {@link RateLimitedException}. */
        RATE_LIMITED,
        /** Timeout, HTTP 5xx, deadlock, still failing after retries. Expect {@link TransientException}. */
        TRANSIENT,
        /** A failure retrying cannot fix (other HTTP 4xx, missing table). Expect {@link PermanentException}. */
        PERMANENT,
        /** The given order comes back unreadable: a required field missing or of another type. */
        MALFORMED_ORDER
    }

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final int SEEDED = 25;
    private static final int MAX_PAGES = 1_000;

    protected abstract ErpConnector connectorWith(List<SeedOrder> orders);

    protected abstract ErpConnector connectorFailingWith(Failure failure, SeedOrder order);

    /** Failures the backend can produce. A JDBC connector, for example, cannot be rate limited. Default: all. */
    protected Set<Failure> applicableFailures() {
        return EnumSet.allOf(Failure.class);
    }

    /** Credentials and tokens the connector was given. None may appear in any exception message. */
    protected Set<String> secrets() {
        return Set.of();
    }

    // ---- reading --------------------------------------------------------------------------------------------------

    @Test
    void readsEveryOrderExactlyOnceAcrossPages() {
        List<SeedOrder> seeds = seeds();
        Read read = readAll(connectorWith(seeds), BASE, BASE.plus(Duration.ofDays(1)));

        assertThat(read.pages()).as("pages read; configure a page size of 10 or less").isGreaterThan(1);
        assertThat(read.orders()).extracting(Order::externalId)
                .as("every seeded order, none duplicated within one read")
                .containsExactlyInAnyOrderElementsOf(seeds.stream().map(SeedOrder::externalId).toList());
    }

    @Test
    void returnsOnlyOrdersChangedInsideTheWindow() {
        ErpConnector connector = connectorWith(seeds());
        Instant from = minute(5);
        Instant to = minute(15);

        Set<String> ids = ids(readAll(connector, from, to).orders());

        Set<String> expected = idsOfMinutes(5, 15);
        assertThat(ids).as("orders changed in [from, to)").containsAll(expected);
        if (connector.info().capabilities().contains(ConnectorInfo.Capability.FILTER_BY_UPDATE_DATE)) {
            assertThat(ids).as("start inclusive, end exclusive, nothing outside").isEqualTo(expected);
        }
    }

    @Test
    void readingTwiceGivesTheSameOrders() {
        ErpConnector connector = connectorWith(seeds());
        Instant to = BASE.plus(Duration.ofDays(1));

        Set<Order> first = new HashSet<>(readAll(connector, BASE, to).orders());
        Set<Order> second = new HashSet<>(readAll(connector, BASE, to).orders());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void fetchOrderFindsAnOrderAndReportsAMissingOneAsEmpty() {
        ErpConnector connector = connectorWith(seeds());

        assertThat(connector.fetchOrder(id(7))).map(Order::externalId).contains(id(7));
        assertThat(connector.fetchOrder("does-not-exist")).isEmpty();
    }

    @Test
    void everyOrderCarriesTheConnectorErpId() {
        ErpConnector connector = connectorWith(seeds());
        String erpId = connector.info().erpId();

        assertThat(erpId).isNotBlank();
        assertThat(readAll(connector, BASE, BASE.plus(Duration.ofDays(1))).orders())
                .extracting(Order::source).containsOnly(erpId);
    }

    // ---- data rules -----------------------------------------------------------------------------------------------

    @Test
    void dataTheErpDoesNotGiveStaysUnknown() {
        SeedOrder bare = new SeedOrder("bare-1", minute(1), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());

        Order order = only(connectorWith(List.of(bare)));

        assertThat(order.customer().flatMap(Customer::document)).as("document").isEmpty();
        assertThat(order.customer().flatMap(Customer::email)).as("e-mail").isEmpty();
        assertThat(order.customer().flatMap(Customer::phone)).as("phone").isEmpty();
        assertThat(order.channel().type()).as("channel never assumed DIRECT").isEqualTo(SalesChannel.Type.UNKNOWN);
        assertThat(order.channel().raw()).as("raw channel").isEmpty();
    }

    @Test
    void unrecognizedChannelStaysUnknownAndKeepsItsText() {
        String nonsense = "unrecognized-channel-7f3a";
        SeedOrder odd = new SeedOrder("odd-1", minute(1), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(nonsense));

        Order order = only(connectorWith(List.of(odd)));

        assertThat(order.channel().type()).as("a channel the connector cannot map is not guessed")
                .isEqualTo(SalesChannel.Type.UNKNOWN);
        assertThat(order.channel().raw()).as("the ERP's text is kept for the consumer").contains(nonsense);
    }

    @Test
    void invalidDocumentIsKeptAndFlaggedWithoutAbortingTheRead() {
        SeedOrder invalid = seed(1, SyntheticDocuments.invalidCpf(1));
        SeedOrder valid = seed(2, SyntheticDocuments.validCpf(2));

        List<Order> orders = readAll(connectorWith(List.of(invalid, valid)), BASE, BASE.plus(Duration.ofDays(1)))
                .orders();

        assertThat(orders).extracting(Order::externalId).containsExactlyInAnyOrder(id(1), id(2));
        Order flagged = orders.stream().filter(o -> o.externalId().equals(id(1))).findFirst().orElseThrow();
        assertThat(flagged.customer().flatMap(Customer::document)).as("document kept")
                .map(TaxDocument::checkDigitsValid).contains(false);
        assertThat(flagged.anomalies()).extracting(Anomaly::code).contains(Anomaly.Code.INVALID_DOCUMENT);
        Order clean = orders.stream().filter(o -> o.externalId().equals(id(2))).findFirst().orElseThrow();
        assertThat(clean.anomalies()).extracting(Anomaly::code).doesNotContain(Anomaly.Code.INVALID_DOCUMENT);
    }

    // ---- contract -------------------------------------------------------------------------------------------------

    @Test
    void rejectsAnEmptyOrReversedWindow() {
        ErpConnector connector = connectorWith(seeds());

        assertThatThrownBy(() -> connector.listOrders(minute(5), minute(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.listOrders(minute(6), minute(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsACursorItDidNotIssue() {
        ErpConnector connector = connectorWith(seeds());

        assertThatThrownBy(() -> connector.listOrders("not-a-cursor-from-this-connector"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- failures -------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Failure.class)
    void failuresSurfaceAsTypedErrors(Failure failure) {
        assumeTrue(applicableFailures().contains(failure), failure + " does not apply to this connector");
        SeedOrder order = seed(3, SyntheticDocuments.validCpf(3));
        ErpConnector connector = connectorFailingWith(failure, order);

        Throwable thrown = catchThrowableOfType(ConnectorException.class,
                () -> connector.listOrders(BASE, BASE.plus(Duration.ofDays(1))));

        assertThat(thrown).as("listOrders must throw a ConnectorException").isNotNull();
        switch (failure) {
            case AUTHENTICATION -> assertThat(thrown).isInstanceOf(AuthenticationException.class);
            case RATE_LIMITED -> assertThat(thrown).isInstanceOf(RateLimitedException.class);
            case TRANSIENT -> assertThat(thrown).isInstanceOf(TransientException.class);
            case PERMANENT -> assertThat(thrown).isInstanceOf(PermanentException.class);
            case MALFORMED_ORDER -> {
                assertThat(thrown).isInstanceOf(UnexpectedFormatException.class);
                UnexpectedFormatException format = (UnexpectedFormatException) thrown;
                assertThat(format.orderExternalId()).as("which order").contains(order.externalId());
                assertThat(format.field()).as("which field").isNotBlank();
            }
        }
    }

    @Test
    void checkConnectionReportsRejectedCredentials() {
        assumeTrue(applicableFailures().contains(Failure.AUTHENTICATION), "AUTHENTICATION does not apply");
        ErpConnector connector = connectorFailingWith(Failure.AUTHENTICATION, seed(4, SyntheticDocuments.validCpf(4)));

        assertThatThrownBy(connector::checkConnection).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void errorMessagesCarryNoPersonalDataOrSecrets() {
        SeedOrder order = new SeedOrder(id(5), minute(5), Optional.of(SyntheticDocuments.validCpf(5)),
                Optional.of("leak.check@example.com"), Optional.of("5511987650005"), Optional.of("MARKETPLACE"));
        List<String> forbidden = new ArrayList<>(List.of(SyntheticDocuments.validCpf(5), "leak.check@example.com",
                "5511987650005"));
        forbidden.addAll(secrets());

        for (Failure failure : applicableFailures()) {
            ErpConnector connector = connectorFailingWith(failure, order);
            Throwable thrown = catchThrowableOfType(ConnectorException.class,
                    () -> connector.listOrders(BASE, BASE.plus(Duration.ofDays(1))));
            if (thrown == null) {
                fail(failure + ": listOrders did not throw");
            }
            String text = describeChain(thrown);
            for (String value : forbidden) {
                assertThat(text).as("%s exception messages", failure).doesNotContain(value);
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** Orders read from all pages of one window, and how many pages it took. */
    private record Read(List<Order> orders, int pages) {
    }

    private static Read readAll(ErpConnector connector, Instant from, Instant to) {
        List<Order> orders = new ArrayList<>();
        OrderPage page = connector.listOrders(from, to);
        int pages = 1;
        orders.addAll(page.orders());
        while (page.nextCursor().isPresent()) {
            if (++pages > MAX_PAGES) {
                fail("more than " + MAX_PAGES + " pages: the cursor never ends");
            }
            page = connector.listOrders(page.nextCursor().get());
            orders.addAll(page.orders());
        }
        return new Read(orders, pages);
    }

    private static Order only(ErpConnector connector) {
        List<Order> orders = readAll(connector, BASE, BASE.plus(Duration.ofDays(1))).orders();
        assertThat(orders).hasSize(1);
        return orders.getFirst();
    }

    private static List<SeedOrder> seeds() {
        return IntStream.range(0, SEEDED).mapToObj(i -> seed(i, SyntheticDocuments.validCpf(i))).toList();
    }

    private static SeedOrder seed(int i, String cpf) {
        return new SeedOrder(id(i), minute(i), Optional.of(cpf), Optional.of("buyer" + i + "@example.com"),
                Optional.of(String.format("55119000%05d", i)), Optional.of("MARKETPLACE"));
    }

    private static String id(int i) {
        return String.format("order-%02d", i);
    }

    private static Instant minute(int i) {
        return BASE.plus(Duration.ofMinutes(i));
    }

    private static Set<String> idsOfMinutes(int fromInclusive, int toExclusive) {
        Set<String> ids = new HashSet<>();
        IntStream.range(fromInclusive, toExclusive).forEach(i -> ids.add(id(i)));
        return ids;
    }

    private static Set<String> ids(List<Order> orders) {
        return new HashSet<>(orders.stream().map(Order::externalId).toList());
    }

    /** Messages and string forms of the exception and every cause. */
    private static String describeChain(Throwable thrown) {
        StringBuilder text = new StringBuilder();
        Set<Throwable> seen = new HashSet<>();
        for (Throwable t = thrown; t != null && seen.add(t); t = t.getCause()) {
            text.append(t).append('\n');
        }
        return text.toString();
    }
}
