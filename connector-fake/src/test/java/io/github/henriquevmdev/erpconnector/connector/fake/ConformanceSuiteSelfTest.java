package io.github.henriquevmdev.erpconnector.connector.fake;

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
import io.github.henriquevmdev.erpconnector.core.model.Order;
import io.github.henriquevmdev.erpconnector.core.model.SalesChannel;
import io.github.henriquevmdev.erpconnector.testkit.ConnectorConformanceSuite;
import io.github.henriquevmdev.erpconnector.testkit.SeedOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Proves the conformance suite fails when it should: each broken connector below has one defect, and the suite must
 * fail exactly the check that targets it. A suite that passes everything proves nothing.
 */
class ConformanceSuiteSelfTest {

    static Stream<Arguments> defects() {
        return Stream.of(
                // Both reads lose the same pages, so "reading twice" still agrees; the window spans several pages.
                Arguments.of(LosesPagesAfterTheFirst.class, Set.of("readsEveryOrderExactlyOnceAcrossPages",
                        "returnsOnlyOrdersChangedInsideTheWindow")),
                // Every check that counts the orders of a read sees the duplicate.
                Arguments.of(DuplicatesAnOrder.class, Set.of("readsEveryOrderExactlyOnceAcrossPages",
                        "dataTheErpDoesNotGiveStaysUnknown", "unrecognizedChannelStaysUnknownAndKeepsItsText",
                        "invalidDocumentIsKeptAndFlaggedWithoutAbortingTheRead")),
                Arguments.of(ReadsEverythingInOnePage.class, Set.of("readsEveryOrderExactlyOnceAcrossPages")),
                Arguments.of(IgnoresTheWindow.class, Set.of("returnsOnlyOrdersChangedInsideTheWindow")),
                Arguments.of(ChangesBetweenReads.class, Set.of("readingTwiceGivesTheSameOrders")),
                Arguments.of(InventsAMissingOrder.class, Set.of("fetchOrderFindsAnOrderAndReportsAMissingOneAsEmpty")),
                Arguments.of(UsesAnotherSource.class, Set.of("everyOrderCarriesTheConnectorErpId")),
                Arguments.of(AssumesDirectChannel.class, Set.of("dataTheErpDoesNotGiveStaysUnknown",
                        "unrecognizedChannelStaysUnknownAndKeepsItsText")),
                Arguments.of(GuessesUnrecognizedChannel.class, Set.of("unrecognizedChannelStaysUnknownAndKeepsItsText")),
                Arguments.of(DropsTheRawChannel.class, Set.of("unrecognizedChannelStaysUnknownAndKeepsItsText")),
                Arguments.of(DropsAnomalies.class, Set.of("invalidDocumentIsKeptAndFlaggedWithoutAbortingTheRead")),
                Arguments.of(AcceptsAReversedWindow.class, Set.of("rejectsAnEmptyOrReversedWindow")),
                Arguments.of(AcceptsAForeignCursor.class, Set.of("rejectsACursorItDidNotIssue")),
                Arguments.of(ReportsRateLimitAsTransient.class, Set.of("failuresSurfaceAsTypedErrors")),
                Arguments.of(IgnoresRejectedCredentialsOnCheck.class, Set.of("checkConnectionReportsRejectedCredentials")),
                Arguments.of(LeaksTheCpfInMessages.class, Set.of("errorMessagesCarryNoPersonalDataOrSecrets")),
                Arguments.of(LeaksASecretInMessages.class, Set.of("errorMessagesCarryNoPersonalDataOrSecrets")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defects")
    void suiteFailsExactlyTheChecksTheDefectBreaks(Class<? extends ConnectorConformanceSuite> broken,
                                                    Set<String> expectedFailures) {
        Set<String> failed = EngineTestKit.engine("junit-jupiter").selectors(selectClass(broken)).execute()
                .testEvents().failed().stream()
                .map(event -> event.getTestDescriptor().getSource()
                        .filter(org.junit.platform.engine.support.descriptor.MethodSource.class::isInstance)
                        .map(source -> ((org.junit.platform.engine.support.descriptor.MethodSource) source).getMethodName())
                        .orElse(event.getTestDescriptor().getDisplayName()))
                .collect(Collectors.toSet());

        assertThat(failed).isEqualTo(expectedFailures);
    }

    // ---- the broken connectors ------------------------------------------------------------------------------------

    /** Runs the suite against the fake, with one defect applied by {@link #breakIt}. */
    abstract static class Broken extends ConnectorConformanceSuite {

        abstract ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing);

        int pageSize() {
            return FakeConnectorConformanceTest.PAGE_SIZE;
        }

        @Override
        protected ErpConnector connectorWith(List<SeedOrder> orders) {
            return breakIt(new FakeConnector(FakeErp.holding(FakeConnectorConformanceTest.rows(orders)), pageSize()),
                    Optional.empty());
        }

        @Override
        protected ErpConnector connectorFailingWith(Failure failure, SeedOrder order) {
            return breakIt(new FakeConnectorConformanceTest().connectorFailingWith(failure, order), Optional.of(order));
        }
    }

    /** Passes every call through; defects override what they break. */
    static class Delegating implements ErpConnector {
        final ErpConnector good;

        Delegating(ErpConnector good) {
            this.good = good;
        }

        @Override
        public ConnectorInfo info() {
            return good.info();
        }

        @Override
        public void checkConnection() {
            good.checkConnection();
        }

        @Override
        public OrderPage listOrders(Instant changedFrom, Instant changedTo) {
            return good.listOrders(changedFrom, changedTo);
        }

        @Override
        public OrderPage listOrders(String cursor) {
            return good.listOrders(cursor);
        }

        @Override
        public Optional<Order> fetchOrder(String externalId) {
            return good.fetchOrder(externalId);
        }
    }

    static final class LosesPagesAfterTheFirst extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(Instant from, Instant to) {
                    return new OrderPage(good.listOrders(from, to).orders(), Optional.empty());
                }
            };
        }
    }

    static final class DuplicatesAnOrder extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(Instant from, Instant to) {
                    OrderPage page = good.listOrders(from, to);
                    if (page.orders().isEmpty()) {
                        return page;
                    }
                    List<Order> doubled = Stream.concat(page.orders().stream(), Stream.of(page.orders().getFirst()))
                            .toList();
                    return new OrderPage(doubled, page.nextCursor());
                }
            };
        }
    }

    static final class ReadsEverythingInOnePage extends Broken {
        @Override
        int pageSize() {
            return 1_000;
        }

        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return good;
        }
    }

    static final class IgnoresTheWindow extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(Instant from, Instant to) {
                    good.listOrders(from, to); // still validates the window
                    return good.listOrders(Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z"));
                }
            };
        }
    }

    static final class ChangesBetweenReads extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            AtomicInteger reads = new AtomicInteger();
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(Instant from, Instant to) {
                    int read = reads.incrementAndGet();
                    OrderPage page = good.listOrders(from, to);
                    List<Order> stamped = page.orders().stream()
                            .map(o -> copy(o, o.source(), Optional.of("read-" + read), o.channel(), o.anomalies()))
                            .toList();
                    return new OrderPage(stamped, page.nextCursor());
                }
            };
        }
    }

    static final class InventsAMissingOrder extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public Optional<Order> fetchOrder(String externalId) {
                    return good.fetchOrder(externalId).or(() -> good.fetchOrder("order-00"));
                }
            };
        }
    }

    static final class UsesAnotherSource extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return mapOrders(good, o -> copy(o, "another-erp", o.number(), o.channel(), o.anomalies()));
        }
    }

    static final class AssumesDirectChannel extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return mapOrders(good, o -> o.channel().type() != SalesChannel.Type.UNKNOWN ? o
                    : copy(o, o.source(), o.number(), new SalesChannel(SalesChannel.Type.DIRECT, Optional.empty()),
                    o.anomalies()));
        }
    }

    static final class GuessesUnrecognizedChannel extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return mapOrders(good, o -> o.channel().type() == SalesChannel.Type.UNKNOWN && o.channel().raw().isPresent()
                    ? copy(o, o.source(), o.number(), new SalesChannel(SalesChannel.Type.DIRECT, o.channel().raw()),
                    o.anomalies())
                    : o);
        }
    }

    static final class DropsTheRawChannel extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return mapOrders(good, o -> copy(o, o.source(), o.number(),
                    new SalesChannel(o.channel().type(), Optional.empty()), o.anomalies()));
        }
    }

    static final class DropsAnomalies extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return mapOrders(good, o -> copy(o, o.source(), o.number(), o.channel(), List.of()));
        }
    }

    static final class AcceptsAReversedWindow extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(Instant from, Instant to) {
                    return from.isBefore(to) ? good.listOrders(from, to) : new OrderPage(List.of(), Optional.empty());
                }
            };
        }
    }

    static final class AcceptsAForeignCursor extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(String cursor) {
                    try {
                        return good.listOrders(cursor);
                    } catch (IllegalArgumentException e) {
                        return new OrderPage(List.of(), Optional.empty());
                    }
                }
            };
        }
    }

    static final class ReportsRateLimitAsTransient extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public OrderPage listOrders(Instant from, Instant to) {
                    try {
                        return good.listOrders(from, to);
                    } catch (RateLimitedException e) {
                        throw new TransientException("busy", e.getCause());
                    }
                }
            };
        }
    }

    static final class IgnoresRejectedCredentialsOnCheck extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return new Delegating(good) {
                @Override
                public void checkConnection() {
                    try {
                        good.checkConnection();
                    } catch (AuthenticationException ignored) {
                        // defect: pretends the connection is fine
                    }
                }
            };
        }
    }

    static final class LeaksTheCpfInMessages extends Broken {
        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return rethrowWithMessage(good, failing.flatMap(SeedOrder::customerDocument).orElse(""));
        }
    }

    static final class LeaksASecretInMessages extends Broken {
        @Override
        protected Set<String> secrets() {
            return Set.of("s3cr3t-db-password");
        }

        @Override
        ErpConnector breakIt(ErpConnector good, Optional<SeedOrder> failing) {
            return rethrowWithMessage(good, "login failed with password s3cr3t-db-password");
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    interface OrderMapper {
        Order map(Order order);
    }

    static ErpConnector mapOrders(ErpConnector good, OrderMapper mapper) {
        return new Delegating(good) {
            @Override
            public OrderPage listOrders(Instant from, Instant to) {
                return remap(good.listOrders(from, to));
            }

            @Override
            public OrderPage listOrders(String cursor) {
                return remap(good.listOrders(cursor));
            }

            @Override
            public Optional<Order> fetchOrder(String externalId) {
                return good.fetchOrder(externalId).map(mapper::map);
            }

            private OrderPage remap(OrderPage page) {
                return new OrderPage(page.orders().stream().map(mapper::map).toList(), page.nextCursor());
            }
        };
    }

    /** Same exception type, but a message that adds {@code leak}. */
    static ErpConnector rethrowWithMessage(ErpConnector good, String leak) {
        return new Delegating(good) {
            @Override
            public OrderPage listOrders(Instant from, Instant to) {
                try {
                    return good.listOrders(from, to);
                } catch (ConnectorException e) {
                    String message = e.getMessage() + " " + leak;
                    throw switch (e) {
                        case AuthenticationException a -> new AuthenticationException(message, a);
                        case RateLimitedException r -> new RateLimitedException(message, r.retryAfter(), r);
                        case TransientException t -> new TransientException(message, t);
                        case PermanentException p -> new PermanentException(message, OptionalInt.empty(), p);
                        case UnexpectedFormatException u ->
                                new UnexpectedFormatException(message, u.orderExternalId(), u.field(), u);
                    };
                }
            }
        };
    }

    static Order copy(Order o, String source, Optional<String> number, SalesChannel channel, List<Anomaly> anomalies) {
        return new Order(source, o.externalId(), number, o.channelOrderNumber(), o.createdDate(), o.createdAt(),
                o.updatedAt(), o.status(), channel, o.customer(), o.items(), o.payments(), o.shipping(), o.discount(),
                o.total(), o.invoiceAccessKey(), anomalies);
    }
}
