package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.OrderItem;
import tacos.OrderStatus;
import tacos.StockReservation;
import tacos.TacoOrder;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.dto.ReorderRequest;
import tacos.api.error.ApiException;
import tacos.api.error.ConflictException;
import tacos.api.error.NotFoundException;
import tacos.data.OrderRepository;
import tacos.inventory.InventoryService;
import tacos.testsupport.Fixtures;

// TC-07, TC-14, TC-18, TC-24, TC-34: caso de uso "colocar orden".
class OrderServiceTest {

    private Map<String, Ingredient> catalog;
    private EmailOrderService emailOrderService;
    private InventoryService inventory;
    private TransactionalOrderService transactional;
    private IdempotencyService idempotency;
    private OrderRepository orderRepo;
    private SimpleMeterRegistry registry;
    private OrderService service;
    private AtomicInteger saves;

    @BeforeEach
    void setUp() {
        catalog = Fixtures.catalog();
        emailOrderService = mock(EmailOrderService.class);
        inventory = mock(InventoryService.class);
        transactional = mock(TransactionalOrderService.class);
        idempotency = mock(IdempotencyService.class);
        orderRepo = mock(OrderRepository.class);
        registry = new SimpleMeterRegistry();
        service = new OrderService(Fixtures.draftFactory(catalog), emailOrderService, inventory, transactional,
            new OrderStateService(Fixtures.CLOCK), idempotency, orderRepo, new TacoMetricsService(registry));

        saves = new AtomicInteger();
        when(inventory.reserve(anyString(), anyString(), anyMap())).thenReturn(Mono.just(new StockReservation()));
        when(inventory.release(anyString())).thenReturn(Mono.just(true));
        when(transactional.saveNewOrder(any(), any())).thenAnswer(inv -> Mono.defer(() -> {
            saves.incrementAndGet();
            return Mono.just((TacoOrder) inv.getArgument(0));
        }));
        when(idempotency.hash(any(OrderCreateRequest.class))).thenReturn("hash-1");
        when(idempotency.hash(anyString())).thenReturn("hash-r");
        when(idempotency.fail(anyString(), anyString())).thenReturn(Mono.empty());
        when(idempotency.complete(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
    }

    // ---- TC-14 ----

    @Test
    void serverCalculatesPricesAndTwoUnitsDoubleTheLine() {
        OrderCreateRequest request = Fixtures.orderRequest(
            Fixtures.item(Fixtures.tacoRequest("Classic", "FLTO", "GRBF", "CHED"), 2));

        StepVerifier.create(service.placeOrder(request, Fixtures.user(Fixtures.USER_ID), null))
            .assertNext(placement -> {
                TacoOrder order = placement.getOrder();
                OrderItem line = order.getItems().get(0);
                assertThat(line.getUnitPriceAtPurchase()).isEqualByComparingTo("2.35");
                assertThat(line.getSubtotal()).isEqualByComparingTo("4.70");
                assertThat(order.getTotal()).isEqualByComparingTo("4.70");
                assertThat(order.getCurrency()).isEqualTo("USD");
                assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
                assertThat(order.getUserId()).isEqualTo(Fixtures.USER_ID);
                assertThat(order.getPaymentLast4()).isEqualTo("1111");
            })
            .verifyComplete();
    }

    @Test
    void couponIsAppliedOnServerTotal() {
        OrderCreateRequest request = Fixtures.orderRequest(
            Fixtures.item(Fixtures.tacoRequest("Classic", "FLTO", "GRBF", "CHED"), 2));
        request.setDiscountCode(" abcdef ");

        TacoOrder order = service.placeOrder(request, Fixtures.user(Fixtures.USER_ID), null).block().getOrder();

        assertThat(order.getDiscountCode()).isEqualTo("ABCDEF");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.47");
        assertThat(order.getTotal()).isEqualByComparingTo("4.23");
        assertThat(registry.counter("tacocloud.coupons.applied").count()).isEqualTo(1.0);
    }

    @Test
    void excessiveQuantityIsRejected() {
        OrderCreateRequest request = Fixtures.orderRequest(
            Fixtures.item(Fixtures.tacoRequest("Classic", "FLTO", "GRBF"), 11));

        StepVerifier.create(service.placeOrder(request, Fixtures.user(Fixtures.USER_ID), null))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_QUANTITY"))
            .verify();
        verify(inventory, never()).reserve(anyString(), anyString(), anyMap());
    }

    @Test
    void paymentMethodOfAnotherUserIsRejected() {
        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.OTHER_USER_ID), null))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("PAYMENT_METHOD_INVALID"))
            .verify();
    }

    // ---- TC-18: la validación ocurre antes de reservar inventario ----

    @Test
    void invalidDesignIsRejectedBeforeReservingInventory() {
        OrderCreateRequest request = Fixtures.orderRequest(
            Fixtures.item(Fixtures.tacoRequest("Two wraps", "FLTO", "COTO", "GRBF"), 1));

        StepVerifier.create(service.placeOrder(request, Fixtures.user(Fixtures.USER_ID), null))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getViolations())
                .extracting("code").containsExactly("MULTIPLE_BASES"))
            .verify();
        verify(inventory, never()).reserve(anyString(), anyString(), anyMap());
        verify(transactional, never()).saveNewOrder(any(), any());
    }

    @Test
    void quoteUsesSameRulesAndNeverSavesOrReserves() {
        StepVerifier.create(service.quote(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)))
            .assertNext(order -> assertThat(order.getTotal()).isEqualByComparingTo("2.35"))
            .verifyComplete();
        verify(inventory, never()).reserve(anyString(), anyString(), anyMap());
        verify(transactional, never()).saveNewOrder(any(), any());
    }

    // ---- TC-16: si falla el guardado, se libera lo reservado ----

    @Test
    void failureAfterReservationReleasesInventory() {
        when(transactional.saveNewOrder(any(), any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID), null))
            .expectError(IllegalStateException.class)
            .verify();
        verify(inventory).release(anyString());
        assertThat(registry.counter("tacocloud.orders.placed", "source", "api", "result", "failed").count()).isEqualTo(1.0);
    }

    @Test
    void insufficientStockIsCountedOnce() {
        when(inventory.reserve(anyString(), anyString(), anyMap()))
            .thenReturn(Mono.error(new ConflictException(InventoryService.INSUFFICIENT_STOCK, "no stock")));

        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID), null))
            .expectError(ConflictException.class)
            .verify();
        assertThat(registry.counter("tacocloud.inventory.rejections").count()).isEqualTo(1.0);
    }

    // ---- TC-07 ----

    @Test
    void emailOrderIsConvertedSavedAndRegisteredExactlyOnce() {
        TacoOrder draft = service.quote(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)).block();
        AtomicInteger conversions = new AtomicInteger();
        when(emailOrderService.convertEmailOrderToDomainOrder(any())).thenReturn(Mono.defer(() -> {
            conversions.incrementAndGet();
            return Mono.just(draft);
        }));

        Mono<TacoOrder> result = service.placeFromEmail(Mono.just(new EmailOrder()));
        assertThat(conversions.get()).isZero();
        assertThat(saves.get()).isZero();

        StepVerifier.create(result).expectNextCount(1).verifyComplete();
        assertThat(conversions.get()).isEqualTo(1);
        assertThat(saves.get()).isEqualTo(1);
    }

    @Test
    void emailConversionErrorNeitherSavesNorPublishes() {
        when(emailOrderService.convertEmailOrderToDomainOrder(any()))
            .thenReturn(Mono.error(new NotFoundException("EMAIL_USER_NOT_FOUND", "no user")));

        StepVerifier.create(service.placeFromEmail(Mono.just(new EmailOrder())))
            .expectError(NotFoundException.class).verify();
        verify(inventory, never()).reserve(anyString(), anyString(), anyMap());
        verify(transactional, never()).saveNewOrder(any(), any());
    }

    @Test
    void emailPersistenceErrorDoesNotPublish() {
        TacoOrder draft = service.quote(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)).block();
        when(emailOrderService.convertEmailOrderToDomainOrder(any())).thenReturn(Mono.just(draft));
        // El evento sólo existe dentro de la misma transacción que la orden: si falla, no hay publicación.
        when(transactional.saveNewOrder(any(), any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(service.placeFromEmail(Mono.just(new EmailOrder())))
            .expectError(IllegalStateException.class).verify();
        verify(inventory).release(anyString());
    }

    // ---- TC-34 ----

    @Test
    void repeatedIdempotencyKeyReturnsTheSameOrderWithoutCreatingAnother() {
        TacoOrder existing = new TacoOrder();
        existing.setId("order-1");
        when(idempotency.begin(Fixtures.USER_ID, "key-12345678", "hash-1")).thenReturn(Mono.just("order-1"));
        when(orderRepo.findById("order-1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID), "key-12345678"))
            .assertNext(placement -> {
                assertThat(placement.isReplayed()).isTrue();
                assertThat(placement.getOrder().getId()).isEqualTo("order-1");
            })
            .verifyComplete();
        assertThat(saves.get()).isZero();
        verify(inventory, never()).reserve(anyString(), anyString(), anyMap());
    }

    @Test
    void newIdempotencyKeyCreatesOrderAndCompletesInsideTheTransaction() {
        when(idempotency.begin(Fixtures.USER_ID, "key-12345678", "hash-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID), "key-12345678"))
            .assertNext(placement -> assertThat(placement.isReplayed()).isFalse())
            .verifyComplete();

        ArgumentCaptor<IdempotencyService.Key> key = ArgumentCaptor.forClass(IdempotencyService.Key.class);
        verify(transactional).saveNewOrder(any(), key.capture());
        assertThat(key.getValue().getUserId()).isEqualTo(Fixtures.USER_ID);
        assertThat(key.getValue().getValue()).isEqualTo("key-12345678");
    }

    @Test
    void failedPlacementMarksTheKeyAsFailedSoItCanBeRetried() {
        when(idempotency.begin(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(transactional.saveNewOrder(any(), any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID), "key-12345678"))
            .expectError(IllegalStateException.class).verify();
        verify(idempotency).fail(Fixtures.USER_ID, "key-12345678");
    }

    @Test
    void conflictingPayloadWithSameKeyIsRejected() {
        when(idempotency.begin(anyString(), anyString(), anyString()))
            .thenReturn(Mono.error(new ConflictException("IDEMPOTENCY_KEY_REUSED", "different request")));

        StepVerifier.create(service.placeOrder(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID), "key-12345678"))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"))
            .verify();
        assertThat(saves.get()).isZero();
    }

    // ---- TC-24 ----

    private TacoOrder previousOrder(String userId, String unitPrice) {
        TacoOrder original = service.quote(Fixtures.simpleOrderRequest(), Fixtures.user(Fixtures.USER_ID)).block();
        original.setId("old-1");
        original.setUserId(userId);
        original.setStatus(OrderStatus.DELIVERED);
        original.getItems().get(0).setUnitPriceAtPurchase(new BigDecimal(unitPrice));
        original.setTotal(new BigDecimal(unitPrice));
        when(orderRepo.findById("old-1")).thenReturn(Mono.just(original));
        return original;
    }

    private static ReorderRequest reorderRequest(boolean confirm) {
        ReorderRequest request = new ReorderRequest();
        request.setPaymentMethodId(Fixtures.PAYMENT_ID);
        request.setConfirmPriceChange(confirm);
        return request;
    }

    @Test
    void reorderCreatesNewIdentityAndStateAndLeavesOriginalUntouched() {
        TacoOrder original = previousOrder(Fixtures.USER_ID, "2.35");

        StepVerifier.create(service.reorder("old-1", reorderRequest(false), Fixtures.user(Fixtures.USER_ID), null))
            .assertNext(result -> {
                assertThat(result.isConfirmed()).isTrue();
                assertThat(result.getOrder().getId()).isNotEqualTo("old-1");
                assertThat(result.getOrder().getStatus()).isEqualTo(OrderStatus.CREATED);
                assertThat(result.getOrder().getStatusHistory()).hasSize(1);
            })
            .verifyComplete();
        assertThat(original.getId()).isEqualTo("old-1");
        assertThat(original.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderRepo, never()).save(any());
    }

    @Test
    void priceChangeReturnsQuoteWithDifferencesUntilConfirmed() {
        previousOrder(Fixtures.USER_ID, "1.99");

        StepVerifier.create(service.reorder("old-1", reorderRequest(false), Fixtures.user(Fixtures.USER_ID), null))
            .assertNext(result -> {
                assertThat(result.isConfirmed()).isFalse();
                assertThat(result.getDifferences()).anyMatch(d -> d.contains("1.99") && d.contains("2.35"));
                assertThat(result.getOrder().getTotal()).isEqualByComparingTo("2.35");
            })
            .verifyComplete();
        assertThat(saves.get()).isZero();

        StepVerifier.create(service.reorder("old-1", reorderRequest(true), Fixtures.user(Fixtures.USER_ID), null))
            .assertNext(result -> assertThat(result.isConfirmed()).isTrue())
            .verifyComplete();
        assertThat(saves.get()).isEqualTo(1);
    }

    @Test
    void reorderWithUnavailableIngredientIsRejected() {
        previousOrder(Fixtures.USER_ID, "2.35");
        catalog.get("GRBF").setAvailable(false);

        StepVerifier.create(service.reorder("old-1", reorderRequest(true), Fixtures.user(Fixtures.USER_ID), null))
            .expectErrorSatisfies(e -> assertThat(((ApiException) e).getViolations())
                .extracting("code").contains("INGREDIENT_UNAVAILABLE"))
            .verify();
        assertThat(saves.get()).isZero();
    }

    @Test
    void foreignOrderCannotBeReordered() {
        previousOrder(Fixtures.OTHER_USER_ID, "2.35");

        StepVerifier.create(service.reorder("old-1", reorderRequest(true), Fixtures.user(Fixtures.USER_ID), null))
            .expectError(NotFoundException.class).verify();
    }

    @Test
    void reorderRetryWithSameKeyDoesNotCreateAnotherOrder() {
        previousOrder(Fixtures.USER_ID, "2.35");
        TacoOrder created = new TacoOrder();
        created.setId("new-1");
        when(idempotency.begin(Fixtures.USER_ID, "reorder-key-1", "hash-r")).thenReturn(Mono.just("new-1"));
        when(orderRepo.findById("new-1")).thenReturn(Mono.just(created));

        StepVerifier.create(service.reorder("old-1", reorderRequest(true), Fixtures.user(Fixtures.USER_ID), "reorder-key-1"))
            .assertNext(result -> assertThat(result.getOrder().getId()).isEqualTo("new-1"))
            .verifyComplete();
        assertThat(saves.get()).isZero();
        verify(transactional, times(0)).saveNewOrder(any(), isNull());
        verify(inventory, never()).reserve(anyString(), anyString(), anyMap());
        verify(idempotency, never()).complete(anyString(), anyString(), eq("new-1"));
    }
}
