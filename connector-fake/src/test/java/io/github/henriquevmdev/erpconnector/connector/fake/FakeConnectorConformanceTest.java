package io.github.henriquevmdev.erpconnector.connector.fake;

import io.github.henriquevmdev.erpconnector.core.connector.ErpConnector;
import io.github.henriquevmdev.erpconnector.testkit.ConnectorConformanceSuite;
import io.github.henriquevmdev.erpconnector.testkit.SeedOrder;

import java.util.List;

/** The fake connector passes the whole suite. This is how a real connector's test looks. */
class FakeConnectorConformanceTest extends ConnectorConformanceSuite {

    static final int PAGE_SIZE = 4;

    @Override
    protected ErpConnector connectorWith(List<SeedOrder> orders) {
        return new FakeConnector(FakeErp.holding(rows(orders)), PAGE_SIZE);
    }

    @Override
    protected ErpConnector connectorFailingWith(Failure failure, SeedOrder order) {
        FakeErp.Problem problem = switch (failure) {
            case AUTHENTICATION -> FakeErp.Problem.REJECT_CREDENTIALS;
            case RATE_LIMITED -> FakeErp.Problem.TOO_MANY_REQUESTS;
            case TRANSIENT -> FakeErp.Problem.UNAVAILABLE;
            case PERMANENT -> FakeErp.Problem.BAD_REQUEST;
            case MALFORMED_ORDER -> FakeErp.Problem.CORRUPT_ROW;
        };
        return new FakeConnector(FakeErp.failing(problem, rows(List.of(order))), PAGE_SIZE);
    }

    /** Seed orders in the fake ERP's own format. */
    static List<FakeErp.Row> rows(List<SeedOrder> orders) {
        return orders.stream().map(o -> new FakeErp.Row(o.externalId(), o.changedAt(),
                o.customerDocument().orElse(null), o.customerEmail().orElse(null), o.customerPhone().orElse(null),
                o.channel().orElse(null))).toList();
    }
}
