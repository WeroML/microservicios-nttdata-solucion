package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tacos.OrderStatus;
import tacos.OrderStatusHistory;
import tacos.TacoOrder;
import tacos.api.error.ConflictException;
import tacos.api.error.ForbiddenException;
import tacos.testsupport.Fixtures;

// TC-25: matriz completa de transiciones, roles, idempotencia y auditoría.
class OrderStateServiceTest {

    private final OrderStateService stateService = new OrderStateService(Fixtures.CLOCK);

    private static final Set<String> ALLOWED = Set.of(
        "CREATED>ACCEPTED", "ACCEPTED>PREPARING", "PREPARING>READY", "READY>OUT_FOR_DELIVERY",
        "OUT_FOR_DELIVERY>DELIVERED", "CREATED>CANCELLED", "ACCEPTED>CANCELLED");

    static Stream<Arguments> everyPair() {
        Stream.Builder<Arguments> pairs = Stream.builder();
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                if (from != to) {
                    pairs.add(Arguments.of(from, to));
                }
            }
        }
        return pairs.build();
    }

    private static TacoOrder order(OrderStatus status) {
        TacoOrder order = new TacoOrder();
        order.setUserId(Fixtures.USER_ID);
        order.setStatus(status);
        return order;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void fullTransitionMatrix(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateService.isAllowed(from, to)).isEqualTo(ALLOWED.contains(from + ">" + to));

        TacoOrder order = order(from);
        if (ALLOWED.contains(from + ">" + to)) {
            stateService.transition(order, to, Fixtures.admin(), "test", null);
            assertThat(order.getStatus()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> stateService.transition(order, to, Fixtures.admin(), "test", null))
                .isInstanceOf(ConflictException.class);
            assertThat(order.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void happyPathCreatedToReadyAndHistoryIsOrdered() {
        TacoOrder order = order(OrderStatus.CREATED);
        stateService.initialize(order, "habuma", "api");

        stateService.transition(order, OrderStatus.ACCEPTED, Fixtures.kitchen(), "KITCHEN", "claimed");
        stateService.transition(order, OrderStatus.PREPARING, Fixtures.kitchen(), "KITCHEN", null);
        stateService.transition(order, OrderStatus.READY, Fixtures.kitchen(), "KITCHEN", "done");

        assertThat(order.getStatusHistory()).extracting(OrderStatusHistory::getStatus)
            .containsExactly(OrderStatus.CREATED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);
        OrderStatusHistory last = order.getStatusHistory().get(3);
        assertThat(last.getChangedBy()).isEqualTo("kitchen");
        assertThat(last.getOrigin()).isEqualTo("KITCHEN");
        assertThat(last.getReason()).isEqualTo("done");
        assertThat(last.getChangedAt()).isEqualTo(Fixtures.CLOCK.instant());
    }

    @Test
    void createdToDeliveredIsConflict() {
        assertThatThrownBy(() -> stateService.transition(order(OrderStatus.CREATED), OrderStatus.DELIVERED,
            Fixtures.admin(), "api", null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void userCannotMarkDeliveredAndKitchenCannotCancelForTheOwner() {
        assertThatThrownBy(() -> stateService.transition(order(OrderStatus.OUT_FOR_DELIVERY), OrderStatus.DELIVERED,
            Fixtures.user(Fixtures.USER_ID), "api", null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> stateService.transition(order(OrderStatus.CREATED), OrderStatus.CANCELLED,
            Fixtures.kitchen(), "KITCHEN", null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownerCancelsOnlyBeforePreparing() {
        for (OrderStatus status : EnumSet.of(OrderStatus.CREATED, OrderStatus.ACCEPTED)) {
            TacoOrder order = order(status);
            stateService.transition(order, OrderStatus.CANCELLED, Fixtures.user(Fixtures.USER_ID), "api", "changed mind");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
        assertThatThrownBy(() -> stateService.transition(order(OrderStatus.PREPARING), OrderStatus.CANCELLED,
            Fixtures.user(Fixtures.USER_ID), "api", null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> stateService.transition(order(OrderStatus.CREATED), OrderStatus.CANCELLED,
            Fixtures.user(Fixtures.OTHER_USER_ID), "api", null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void repeatingTheCurrentStatusIsIdempotent() {
        TacoOrder order = order(OrderStatus.ACCEPTED);

        boolean changed = stateService.transition(order, OrderStatus.ACCEPTED, Fixtures.kitchen(), "KITCHEN", null);

        assertThat(changed).isFalse();
        assertThat(order.getStatusHistory()).isEmpty();
    }

    @Test
    void auditReasonIsSanitizedAndBounded() {
        TacoOrder order = order(OrderStatus.CREATED);
        String reason = "line1\nline2\r" + new String(new char[300]).replace('\0', 'x');

        stateService.transition(order, OrderStatus.ACCEPTED, Fixtures.kitchen(), "KITCHEN", reason);

        String saved = order.getStatusHistory().get(0).getReason();
        assertThat(saved).doesNotContain("\n", "\r");
        assertThat(saved).hasSizeLessThanOrEqualTo(200);
    }
}
