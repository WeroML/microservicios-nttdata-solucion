package tacos.web.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.OrderItem;
import tacos.TacoOrder;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.dto.ReorderRequest;
import tacos.api.dto.TacoRequest;
import tacos.api.error.ApiException;
import tacos.api.error.NotFoundException;
import tacos.data.OrderRepository;
import tacos.inventory.InventoryService;

/**
 * Caso de uso "colocar orden". Todas las entradas (API, correo, reordenar)
 * pasan por place(): reserva inventario, guarda orden + outbox en una
 * transacción y, si algo falla, libera lo reservado.
 */
@Service
public class OrderService {

    public static final String SOURCE_API = "api";
    public static final String SOURCE_EMAIL = "email";
    public static final String SOURCE_REORDER = "reorder";

    private final OrderDraftFactory draftFactory;
    private final EmailOrderService emailOrderService;
    private final InventoryService inventoryService;
    private final TransactionalOrderService transactionalOrderService;
    private final OrderStateService stateService;
    private final IdempotencyService idempotencyService;
    private final OrderRepository orderRepo;
    private final TacoMetricsService metrics;

    public OrderService(OrderDraftFactory draftFactory, EmailOrderService emailOrderService,
                        InventoryService inventoryService, TransactionalOrderService transactionalOrderService,
                        OrderStateService stateService, IdempotencyService idempotencyService,
                        OrderRepository orderRepo, TacoMetricsService metrics) {
        this.draftFactory = draftFactory;
        this.emailOrderService = emailOrderService;
        this.inventoryService = inventoryService;
        this.transactionalOrderService = transactionalOrderService;
        this.stateService = stateService;
        this.idempotencyService = idempotencyService;
        this.orderRepo = orderRepo;
        this.metrics = metrics;
    }

    // TC-15/TC-17/TC-18: cotización con las mismas reglas que la creación, sin guardar ni reservar.
    public Mono<TacoOrder> quote(OrderCreateRequest request, Actor actor) {
        return draftFactory.build(actor.getUserId(), request);
    }

    // TC-34: con Idempotency-Key, repetir la misma petición devuelve la orden original.
    public Mono<Placement> placeOrder(OrderCreateRequest request, Actor actor, String idempotencyKey) {
        if (idempotencyKey == null) {
            return draftFactory.build(actor.getUserId(), request)
                .flatMap(draft -> place(draft, SOURCE_API, actor.getUsername(), null))
                .map(Placement::created);
        }
        IdempotencyService.validateKey(idempotencyKey);
        IdempotencyService.Key key = new IdempotencyService.Key(actor.getUserId(), idempotencyKey);
        return withIdempotency(key, idempotencyService.hash(request),
            () -> draftFactory.build(actor.getUserId(), request)
                .flatMap(draft -> place(draft, SOURCE_API, actor.getUsername(), key))
                .map(Placement::created));
    }

    // TC-07: convertir, reservar, guardar y registrar el evento en una sola secuencia.
    public Mono<TacoOrder> placeFromEmail(Mono<EmailOrder> emailOrder) {
        return emailOrderService.convertEmailOrderToDomainOrder(emailOrder)
            .flatMap(draft -> place(draft, SOURCE_EMAIL, "email-integration", null));
    }

    /**
     * TC-24: reordenar ejecuta de nuevo el comando de crear orden con las reglas,
     * precios, cupón e inventario actuales. La orden original no se modifica.
     * Si el precio cambió y no se confirmó, se responde una cotización con las diferencias.
     */
    public Mono<Reorder> reorder(String orderId, ReorderRequest request, Actor actor, String idempotencyKey) {
        Mono<Reorder> command = orderRepo.findById(orderId)
            .filter(order -> actor.getUserId().equals(order.getUserId()))
            .switchIfEmpty(Mono.error(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found.")))
            .flatMap(original -> draftFactory.build(actor.getUserId(), toRequest(original, request))
                .flatMap(draft -> {
                    List<String> differences = differences(original, draft);
                    if (!differences.isEmpty() && !request.isConfirmPriceChange()) {
                        return Mono.just(Reorder.quote(draft, differences));
                    }
                    return place(draft, SOURCE_REORDER, actor.getUsername(), null)
                        .map(created -> Reorder.confirmed(created, differences));
                }));

        if (idempotencyKey == null) {
            return command;
        }
        IdempotencyService.validateKey(idempotencyKey);
        IdempotencyService.Key key = new IdempotencyService.Key(actor.getUserId(), idempotencyKey);
        String hash = idempotencyService.hash("reorder|" + orderId + "|" + request.getPaymentMethodId()
            + "|" + request.isConfirmPriceChange());
        return withIdempotency(key, hash, () -> command
            .flatMap(result -> result.isConfirmed()
                ? idempotencyService.complete(key.getUserId(), key.getValue(), result.getOrder().getId())
                    .thenReturn(result)
                // Una cotización no crea nada: la llave queda libre para reintentar.
                : idempotencyService.fail(key.getUserId(), key.getValue()).thenReturn(result))
            .map(Placement::reorder))
            .map(Placement::getReorder);
    }

    private Mono<Placement> withIdempotency(IdempotencyService.Key key, String hash,
                                            Supplier<Mono<Placement>> action) {
        return idempotencyService.begin(key.getUserId(), key.getValue(), hash)
            .flatMap(existingOrderId -> orderRepo.findById(existingOrderId).map(Placement::replayed))
            .switchIfEmpty(Mono.defer(() -> action.get()
                .onErrorResume(error -> idempotencyService.fail(key.getUserId(), key.getValue())
                    .then(Mono.error(error)))));
    }

    private Mono<TacoOrder> place(TacoOrder draft, String source, String changedBy, IdempotencyService.Key key) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            draft.setId(new ObjectId().toHexString());
            stateService.initialize(draft, changedBy, source);
            return inventoryService.reserve(draft.getId(), draft.getId(), InventoryService.quantitiesOf(draft))
                .then(transactionalOrderService.saveNewOrder(draft, key))
                .onErrorResume(error -> inventoryService.release(draft.getId()).then(Mono.error(error)))
                .doOnSuccess(saved -> {
                    metrics.recordPlacement(source, "success", Duration.ofNanos(System.nanoTime() - start));
                    if (saved.getDiscountCode() != null) {
                        metrics.recordCouponApplied();
                    }
                })
                .doOnError(error -> {
                    if (error instanceof ApiException
                        && InventoryService.INSUFFICIENT_STOCK.equals(((ApiException) error).getCode())) {
                        metrics.recordStockRejected();
                    }
                    metrics.recordPlacement(source, "failed", Duration.ofNanos(System.nanoTime() - start));
                });
        });
    }

    // No se clona el documento: se arma un comando nuevo con los datos de negocio.
    private static OrderCreateRequest toRequest(TacoOrder original, ReorderRequest reorder) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setDeliveryName(original.getDeliveryName());
        request.setDeliveryStreet(original.getDeliveryStreet());
        request.setDeliveryCity(original.getDeliveryCity());
        request.setDeliveryState(original.getDeliveryState());
        request.setDeliveryZip(original.getDeliveryZip());
        request.setPaymentMethodId(reorder.getPaymentMethodId());
        request.setDiscountCode(original.getDiscountCode());
        request.setItems(original.getItems().stream().map(item -> {
            TacoRequest taco = new TacoRequest();
            taco.setName(item.getTaco().getName());
            taco.setIngredientIds(item.getTaco().getIngredients().stream()
                .map(i -> i.getId()).collect(Collectors.toList()));
            OrderCreateRequest.OrderItemRequest line = new OrderCreateRequest.OrderItemRequest();
            line.setTaco(taco);
            line.setQuantity(item.getQuantity());
            return line;
        }).collect(Collectors.toList()));
        return request;
    }

    static List<String> differences(TacoOrder original, TacoOrder draft) {
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < original.getItems().size(); i++) {
            OrderItem before = original.getItems().get(i);
            OrderItem now = draft.getItems().get(i);
            if (before.getUnitPriceAtPurchase().compareTo(now.getUnitPriceAtPurchase()) != 0) {
                differences.add("Taco '" + now.getTaco().getName() + "' unit price changed from "
                    + before.getUnitPriceAtPurchase() + " to " + now.getUnitPriceAtPurchase());
            }
        }
        if (!sameAmount(original.getTotal(), draft.getTotal())) {
            differences.add("Total changed from " + original.getTotal() + " to " + draft.getTotal());
        }
        return differences;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    // Resultado de colocar una orden: nueva (201) o repetición idempotente (200).
    public static final class Placement {
        private final TacoOrder order;
        private final boolean replayed;
        private final Reorder reorder;

        private Placement(TacoOrder order, boolean replayed, Reorder reorder) {
            this.order = order;
            this.replayed = replayed;
            this.reorder = reorder;
        }

        static Placement created(TacoOrder order) {
            return new Placement(order, false, null);
        }

        static Placement replayed(TacoOrder order) {
            return new Placement(order, true, Reorder.confirmed(order, new ArrayList<>()));
        }

        static Placement reorder(Reorder reorder) {
            return new Placement(reorder.getOrder(), false, reorder);
        }

        public TacoOrder getOrder() {
            return order;
        }

        public boolean isReplayed() {
            return replayed;
        }

        Reorder getReorder() {
            return reorder;
        }
    }

    public static final class Reorder {
        private final TacoOrder order;
        private final boolean confirmed;
        private final List<String> differences;

        private Reorder(TacoOrder order, boolean confirmed, List<String> differences) {
            this.order = order;
            this.confirmed = confirmed;
            this.differences = differences;
        }

        static Reorder quote(TacoOrder draft, List<String> differences) {
            return new Reorder(draft, false, differences);
        }

        static Reorder confirmed(TacoOrder order, List<String> differences) {
            return new Reorder(order, true, differences);
        }

        public TacoOrder getOrder() {
            return order;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public List<String> getDifferences() {
            return differences;
        }
    }
}
