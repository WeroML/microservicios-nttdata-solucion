package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.OrderItem;
import tacos.OrderStatus;
import tacos.StockReservation;
import tacos.TacoOrder;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.OrderMapper;
import tacos.api.dto.OrderPatchRequest;
import tacos.api.dto.TacoMapper;
import tacos.api.error.ApiException;
import tacos.api.error.BadRequestException;
import tacos.api.error.ConflictException;
import tacos.api.error.ForbiddenException;
import tacos.api.error.NotFoundException;
import tacos.data.OrderRepository;
import tacos.inventory.InventoryService;
import tacos.messaging.contract.OrderEventType;
import tacos.testsupport.Fixtures;

// TC-04, TC-05, TC-23, TC-25: operaciones sobre órdenes existentes con ownership en el servicio.
class OrderManagementServiceTest {

    private OrderRepository orderRepo;
    private ReactiveMongoTemplate mongo;
    private TransactionalOrderService transactional;
    private InventoryService inventory;
    private OrderManagementService service;
    private Map<String, Ingredient> catalog;

    @BeforeEach
    void setUp() {
        catalog = Fixtures.catalog();
        orderRepo = mock(OrderRepository.class);
        mongo = mock(ReactiveMongoTemplate.class);
        transactional = mock(TransactionalOrderService.class);
        inventory = mock(InventoryService.class);
        service = new OrderManagementService(orderRepo, mongo, new OrderStateService(Fixtures.CLOCK), transactional,
            inventory, Fixtures.draftFactory(catalog), new OrderMapper(new TacoMapper(new IngredientMapper())),
            new TacoMetricsService(new SimpleMeterRegistry()), 50);
        when(orderRepo.findById(anyString())).thenReturn(Mono.empty());
        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just((TacoOrder) inv.getArgument(0)));
        when(transactional.saveOrderWithEvent(any(), any())).thenAnswer(inv -> Mono.just((TacoOrder) inv.getArgument(0)));
        when(inventory.release(anyString())).thenReturn(Mono.just(true));
        when(inventory.releaseAllExcept(anyString(), anyString())).thenReturn(Mono.just(true));
        when(inventory.releaseReservation(anyString())).thenReturn(Mono.just(true));
        when(inventory.reserve(anyString(), anyString(), anyMap())).thenReturn(Mono.just(new StockReservation()));
    }

    private TacoOrder existing(String id, String userId, OrderStatus status) {
        TacoOrder order = new TacoOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        order.setDeliveryName("Craig");
        order.setDeliveryState("TX");
        order.setDeliveryZip("76227");
        order.setPaymentMethodId(Fixtures.PAYMENT_ID);
        order.setTotal(new BigDecimal("9.99"));
        OrderItem item = new OrderItem();
        item.setTaco(Fixtures.taco(null, "Classic", catalog, "FLTO", "GRBF"));
        item.setQuantity(1);
        item.setUnitPriceAtPurchase(new BigDecimal("1.75"));
        item.setSubtotal(new BigDecimal("1.75"));
        order.addItem(item);
        when(orderRepo.findById(id)).thenReturn(Mono.just(order));
        return order;
    }

    // ---- TC-04 ----

    @Test
    void changingZipDoesNotTouchStateAndViceVersa() {
        existing("o1", Fixtures.USER_ID, OrderStatus.CREATED);
        OrderPatchRequest zipOnly = new OrderPatchRequest();
        zipOnly.setDeliveryZip("12345");

        StepVerifier.create(service.patch("o1", zipOnly, Fixtures.user(Fixtures.USER_ID)))
            .assertNext(o -> {
                assertThat(o.getDeliveryZip()).isEqualTo("12345");
                assertThat(o.getDeliveryState()).isEqualTo("TX");
            })
            .verifyComplete();

        OrderPatchRequest stateOnly = new OrderPatchRequest();
        stateOnly.setDeliveryState("CA");
        StepVerifier.create(service.patch("o1", stateOnly, Fixtures.user(Fixtures.USER_ID)))
            .assertNext(o -> {
                assertThat(o.getDeliveryState()).isEqualTo("CA");
                assertThat(o.getDeliveryZip()).isEqualTo("12345");
            })
            .verifyComplete();
        verify(orderRepo, times(2)).save(any());
    }

    @Test
    void forbiddenFieldsAreRejectedWith400() {
        OrderPatchRequest patch = new OrderPatchRequest();
        patch.rejectField("total", 0);
        patch.rejectField("status", "DELIVERED");

        StepVerifier.create(service.patch("o1", patch, Fixtures.user(Fixtures.USER_ID)))
            .expectErrorSatisfies(e -> {
                assertThat(e).isInstanceOf(BadRequestException.class);
                assertThat(e.getMessage()).contains("status", "total");
            })
            .verify();
        verify(orderRepo, never()).save(any());
    }

    @Test
    void patchMissingOrderIs404AndForeignOrderIs403() {
        existing("o1", Fixtures.OTHER_USER_ID, OrderStatus.CREATED);
        OrderPatchRequest patch = new OrderPatchRequest();
        patch.setDeliveryZip("12345");

        StepVerifier.create(service.patch("missing", patch, Fixtures.user(Fixtures.USER_ID)))
            .expectError(NotFoundException.class).verify();
        StepVerifier.create(service.patch("o1", patch, Fixtures.user(Fixtures.USER_ID)))
            .expectError(ForbiddenException.class).verify();
        verify(orderRepo, never()).save(any());
    }

    @Test
    void adminCanPatchAnyOrder() {
        existing("o1", Fixtures.OTHER_USER_ID, OrderStatus.CREATED);
        OrderPatchRequest patch = new OrderPatchRequest();
        patch.setDeliveryName("Admin fix");

        StepVerifier.create(service.patch("o1", patch, Fixtures.admin()))
            .assertNext(o -> assertThat(o.getDeliveryName()).isEqualTo("Admin fix"))
            .verifyComplete();
    }

    // ---- TC-05 ----

    @Test
    void putReplacesTheOrderOfThePathKeepingIdentityOwnerDateAndPayment() {
        TacoOrder order = existing("o1", Fixtures.USER_ID, OrderStatus.CREATED);
        java.util.Date placedAt = order.getPlacedAt();

        StepVerifier.create(service.replace("o1", Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)))
            .assertNext(o -> {
                assertThat(o.getId()).isEqualTo("o1");
                assertThat(o.getUserId()).isEqualTo(Fixtures.USER_ID);
                assertThat(o.getPlacedAt()).isEqualTo(placedAt);
                assertThat(o.getPaymentMethodId()).isEqualTo(Fixtures.PAYMENT_ID);
                assertThat(o.getStatus()).isEqualTo(OrderStatus.CREATED);
                assertThat(o.getTotal()).isEqualByComparingTo("2.35");
            })
            .verifyComplete();
        verify(inventory).releaseAllExcept(eq("o1"), anyString());
    }

    @Test
    void putNeverCreatesANewOrder() {
        StepVerifier.create(service.replace("missing", Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)))
            .expectError(NotFoundException.class).verify();
        verify(orderRepo, never()).save(any());
    }

    @Test
    void putCannotChangePaymentOrTouchForeignOrNonCreatedOrders() {
        existing("mine", Fixtures.USER_ID, OrderStatus.CREATED);
        existing("foreign", Fixtures.OTHER_USER_ID, OrderStatus.CREATED);
        existing("cooking", Fixtures.USER_ID, OrderStatus.PREPARING);
        tacos.api.dto.OrderCreateRequest otherPayment = Fixtures.simpleOrderRequest();
        otherPayment.setPaymentMethodId("pm-other");

        StepVerifier.create(service.replace("mine", otherPayment, Fixtures.user(Fixtures.USER_ID)))
            .expectError(BadRequestException.class).verify();
        StepVerifier.create(service.replace("foreign", Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)))
            .expectError(ForbiddenException.class).verify();
        StepVerifier.create(service.replace("cooking", Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)))
            .expectError(ConflictException.class).verify();
        verify(orderRepo, never()).save(any());
    }

    @Test
    void deleteCancelsOwnCreatedOrderAndReleasesInventoryOnce() {
        existing("o1", Fixtures.USER_ID, OrderStatus.CREATED);

        StepVerifier.create(service.cancel("o1", Fixtures.user(Fixtures.USER_ID), "Deleted by client", "api"))
            .assertNext(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED))
            .verifyComplete();
        verify(transactional).saveOrderWithEvent(any(), eq(OrderEventType.CANCELLED));
        verify(inventory, times(1)).release("o1");
    }

    @Test
    void deleteMissingForeignOrPreparingOrder() {
        existing("foreign", Fixtures.OTHER_USER_ID, OrderStatus.CREATED);
        existing("cooking", Fixtures.USER_ID, OrderStatus.PREPARING);

        StepVerifier.create(service.cancel("missing", Fixtures.user(Fixtures.USER_ID), null, "api"))
            .expectError(NotFoundException.class).verify();
        StepVerifier.create(service.cancel("foreign", Fixtures.user(Fixtures.USER_ID), null, "api"))
            .expectError(ForbiddenException.class).verify();
        // Una orden en preparación no se elimina ni se cancela: 409.
        StepVerifier.create(service.cancel("cooking", Fixtures.user(Fixtures.USER_ID), null, "api"))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_TRANSITION"))
            .verify();
        verify(transactional, never()).saveOrderWithEvent(any(), any());
        verify(inventory, never()).release(anyString());
    }

    @Test
    void cancellingTwiceIsIdempotentAndReleasesOnlyOnce() {
        existing("o1", Fixtures.USER_ID, OrderStatus.CREATED);

        service.cancel("o1", Fixtures.user(Fixtures.USER_ID), null, "api").block();
        StepVerifier.create(service.cancel("o1", Fixtures.user(Fixtures.USER_ID), null, "api"))
            .assertNext(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED))
            .verifyComplete();
        verify(inventory, times(1)).release("o1");
    }

    // ---- TC-25 ----

    @Test
    void kitchenMovesStatusThroughTheCentralStateService() {
        existing("o1", Fixtures.USER_ID, OrderStatus.CREATED);

        StepVerifier.create(service.updateStatus("o1", OrderStatus.ACCEPTED, "claimed", Fixtures.kitchen()))
            .assertNext(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.ACCEPTED))
            .verifyComplete();
        StepVerifier.create(service.updateStatus("o1", OrderStatus.DELIVERED, null, Fixtures.kitchen()))
            .expectError(ConflictException.class).verify();
        verify(transactional, times(1)).saveOrderWithEvent(any(), eq(OrderEventType.STATUS_CHANGED));
    }

    // ---- TC-23 ----

    @Test
    void historyQueriesOnlyTheAuthenticatedUserNewestFirst() {
        when(mongo.find(any(Query.class), eq(TacoOrder.class))).thenReturn(Flux.empty());

        StepVerifier.create(service.listMine(Fixtures.user(Fixtures.USER_ID), 0, 20))
            .assertNext(page -> assertThat(page.getContent()).isEmpty())
            .verifyComplete();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(TacoOrder.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("\"userId\": \"" + Fixtures.USER_ID + "\"");
        assertThat(query.getValue().getSortObject().toJson()).isEqualTo("{\"placedAt\": -1, \"_id\": -1}");
        assertThat(query.getValue().getLimit()).isEqualTo(21);
    }

    @Test
    void foreignOrderDetailIs404NotForbidden() {
        existing("o1", Fixtures.OTHER_USER_ID, OrderStatus.CREATED);

        StepVerifier.create(service.findMine("o1", Fixtures.user(Fixtures.USER_ID)))
            .expectError(NotFoundException.class).verify();
    }

    @Test
    void adminSearchUsesExplicitFilters() {
        when(mongo.find(any(Query.class), eq(TacoOrder.class))).thenReturn(Flux.empty());

        service.adminSearch(OrderStatus.CREATED, "user-x", 1, 10).block();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(TacoOrder.class));
        assertThat(query.getValue().getQueryObject().get("status")).isEqualTo(OrderStatus.CREATED);
        assertThat(query.getValue().getQueryObject().get("userId")).isEqualTo("user-x");
        assertThat(query.getValue().getSkip()).isEqualTo(10);
    }

    @Test
    void invalidPaginationIs400() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.listMine(Fixtures.user(Fixtures.USER_ID), 0, 500))
            .isInstanceOf(BadRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.listMine(Fixtures.user(Fixtures.USER_ID), -1, 10))
            .isInstanceOf(BadRequestException.class);
    }
}
