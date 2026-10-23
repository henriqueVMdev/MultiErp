package io.github.henriquevmdev.erpconnector.core.connector;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.henriquevmdev.erpconnector.core.RecordAssertions.assertEachComponentRejectsNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorTypesTest {

    @Test
    void connectorInfoCopiesCapabilities() {
        Set<ConnectorInfo.Capability> capabilities = EnumSet.of(ConnectorInfo.Capability.FILTER_BY_UPDATE_DATE);
        ConnectorInfo info = new ConnectorInfo("fake", capabilities);

        capabilities.add(ConnectorInfo.Capability.WEBHOOKS);

        assertThat(info.capabilities()).containsExactly(ConnectorInfo.Capability.FILTER_BY_UPDATE_DATE);
        assertThatThrownBy(() -> info.capabilities().add(ConnectorInfo.Capability.SANDBOX))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void connectorInfoNeedsAnErpId() {
        assertThatThrownBy(() -> new ConnectorInfo(" ", Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderPageCopiesOrders() {
        List<io.github.henriquevmdev.erpconnector.core.model.Order> orders = new ArrayList<>();
        OrderPage page = new OrderPage(orders, Optional.of("cursor"));

        assertThatThrownBy(() -> page.orders().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(page.nextCursor()).contains("cursor");
    }

    @Test
    void componentsRejectNull() throws Exception {
        assertEachComponentRejectsNull(new ConnectorInfo("fake", Set.of()));
        assertEachComponentRejectsNull(new OrderPage(List.of(), Optional.empty()));
    }
}
