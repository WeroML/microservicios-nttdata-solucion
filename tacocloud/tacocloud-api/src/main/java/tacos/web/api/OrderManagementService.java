package tacos.web.api;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.OrderStatus;
import tacos.TacoOrder;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.dto.OrderPatchRequest;
import tacos.api.dto.OrderSummaryResponse;
import tacos.api.dto.PageResponse;
import tacos.api.error.BadRequestException;
import tacos.api.error.ConflictException;
import tacos.api.error.ForbiddenException;
import tacos.api.error.NotFoundException;
import tacos.api.dto.OrderMapper;
import tacos.data.OrderRepository;
import tacos.inventory.InventoryService;
import tacos.messaging.contract.OrderEventType;

/**
 * Operaciones sobre órdenes existentes. La autorización por dueño se aplica aquí,
 * en el servicio, además de la regla por URL (TC-11).
 *
 * Política de acceso a una orden ajena:
 * - Rutas "me" (/users/me/orders/...): 404, para no revelar que la orden existe (TC-23).
 * - Rutas de operación (/orders/{id} PUT/PATCH/DELETE/cancel): 403 (TC-05).
 */
@Service
public class OrderManagementService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("_id"));

    private final OrderRepository orderRepo;
    private final ReactiveMongoTemplate mongo;
    private final OrderStateService stateService;
    private final TransactionalOrderService transactionalOrderService;
    private final InventoryService inventoryService;
    private final OrderDraftFactory draftFactory;
    private final OrderMapper orderMapper;
    private final TacoMetricsService metrics;
    private final int maxPageSize;

    public OrderManagementService(OrderRepository orderRepo, ReactiveMongoTemplate mongo,
                                  OrderStateService stateService,
                                  TransactionalOrderService transactionalOrderService,
                                  InventoryService inventoryService, OrderDraftFactory draftFactory,
                                  OrderMapper orderMapper, TacoMetricsService metrics,
                                  @Value("${tacocloud.api.max-page-size:50}") int maxPageSize) {
        this.orderRepo = orderRepo;
        this.mongo = mongo;
        this.stateService = stateService;
        this.transactionalOrderService = transactionalOrderService;
        this.inventoryService = inventoryService;
        this.draftFactory = draftFactory;
        this.orderMapper = orderMapper;
        this.metrics = metrics;
        this.maxPageSize = maxPageSize;
    }

    // ---- Consultas (TC-23) ----

    public Mono<TacoOrder> findMine(String orderId, Actor actor) {
        return orderRepo.findById(orderId)
            .filter(order -> actor.getUserId().equals(order.getUserId()))
            .switchIfEmpty(Mono.error(() -> orderNotFound()));
    }

    public Mono<PageResponse<OrderSummaryResponse>> listMine(Actor actor, int page, int size) {
        Paging.validate(page, size, maxPageSize);
        return page(Query.query(where("userId").is(actor.getUserId())), page, size);
    }

    public Mono<PageResponse<OrderSummaryResponse>> adminSearch(OrderStatus status, String userId, int page, int size) {
        Paging.validate(page, size, maxPageSize);
        Query query = new Query();
        if (status != null) {
            query.addCriteria(where("status").is(status));
        }
        if (userId != null && !userId.isEmpty()) {
            query.addCriteria(where("userId").is(userId));
        }
        return page(query, page, size);
    }

    // Orden estable (placedAt desc, id desc); se pide un elemento extra para saber si hay otra página.
    private Mono<PageResponse<OrderSummaryResponse>> page(Query query, int page, int size) {
        query.with(NEWEST_FIRST).skip((long) page * size).limit(size + 1);
        return mongo.find(query, TacoOrder.class)
            .map(orderMapper::toSummary)
            .collectList()
            .map(list -> PageResponse.of(list, page, size));
    }

    // ---- Comandos (TC-04, TC-05, TC-25) ----

    /**
     * TC-05: PUT reemplaza los datos de entrega y los tacos de la orden de la ruta.
     * No crea órdenes nuevas, no cambia ID, dueño, fecha, estado ni pago, y sólo
     * se permite mientras la orden sigue CREATED. Los precios se recalculan y el
     * inventario se reserva de nuevo.
     */
    public Mono<TacoOrder> replace(String orderId, OrderCreateRequest request, Actor actor) {
        return findForOperation(orderId, actor)
            .flatMap(existing -> {
                if (existing.getStatus() != OrderStatus.CREATED) {
                    return Mono.error(new ConflictException("ORDER_NOT_EDITABLE",
                        "Only CREATED orders can be replaced."));
                }
                if (!existing.getPaymentMethodId().equals(request.getPaymentMethodId())) {
                    return Mono.error(new BadRequestException("PAYMENT_CHANGE_NOT_ALLOWED",
                        "The payment method of an existing order cannot be changed."));
                }
                return draftFactory.build(existing.getUserId(), request)
                    .flatMap(draft -> applyReplacement(existing, draft));
            });
    }

    private Mono<TacoOrder> applyReplacement(TacoOrder existing, TacoOrder draft) {
        existing.setDeliveryName(draft.getDeliveryName());
        existing.setDeliveryStreet(draft.getDeliveryStreet());
        existing.setDeliveryCity(draft.getDeliveryCity());
        existing.setDeliveryState(draft.getDeliveryState());
        existing.setDeliveryZip(draft.getDeliveryZip());
        existing.setItems(draft.getItems());
        existing.setSubtotal(draft.getSubtotal());
        existing.setDiscountCode(draft.getDiscountCode());
        existing.setDiscountAmount(draft.getDiscountAmount());
        existing.setTotal(draft.getTotal());

        String reservationId = existing.getId() + ":" + UUID.randomUUID();
        return inventoryService.reserve(reservationId, existing.getId(), InventoryService.quantitiesOf(existing))
            .then(orderRepo.save(existing))
            .flatMap(saved -> inventoryService.releaseAllExcept(saved.getId(), reservationId).thenReturn(saved))
            .onErrorResume(error -> inventoryService.releaseReservation(reservationId).then(Mono.error(error)));
    }

    /**
     * TC-04: PATCH con lista blanca. Sólo datos de entrega; cualquier otro campo
     * (ID, usuario, fecha, total, estado, pago, tacos) se rechaza con 400.
     * Se guarda una sola vez al final.
     */
    public Mono<TacoOrder> patch(String orderId, OrderPatchRequest patch, Actor actor) {
        if (!patch.getRejectedFields().isEmpty()) {
            return Mono.error(new BadRequestException("FIELD_NOT_ALLOWED",
                "These fields cannot be changed with PATCH: "
                    + patch.getRejectedFields().keySet().stream().sorted().collect(Collectors.joining(", "))));
        }
        return findForOperation(orderId, actor)
            .flatMap(order -> {
                if (patch.getDeliveryName() != null) {
                    order.setDeliveryName(patch.getDeliveryName());
                }
                if (patch.getDeliveryStreet() != null) {
                    order.setDeliveryStreet(patch.getDeliveryStreet());
                }
                if (patch.getDeliveryCity() != null) {
                    order.setDeliveryCity(patch.getDeliveryCity());
                }
                if (patch.getDeliveryState() != null) {
                    order.setDeliveryState(patch.getDeliveryState());
                }
                if (patch.getDeliveryZip() != null) {
                    order.setDeliveryZip(patch.getDeliveryZip());
                }
                return orderRepo.save(order);
            });
    }

    /**
     * TC-05/TC-25: cancelar (también lo usa DELETE). No hay borrado físico: la orden
     * pasa a CANCELLED para conservar auditoría y eventos. Sólo el dueño o ADMIN, y
     * sólo antes de PREPARING (si no, 409). Libera el inventario una sola vez.
     */
    public Mono<TacoOrder> cancel(String orderId, Actor actor, String reason, String origin) {
        return findForOperation(orderId, actor)
            .flatMap(order -> applyCancellation(order, actor, reason, origin));
    }

    private Mono<TacoOrder> applyCancellation(TacoOrder order, Actor actor, String reason, String origin) {
        if (!stateService.transition(order, OrderStatus.CANCELLED, actor, origin, reason)) {
            return Mono.just(order); // ya estaba cancelada: idempotente
        }
        return transactionalOrderService.saveOrderWithEvent(order, OrderEventType.CANCELLED)
            .flatMap(saved -> inventoryService.release(saved.getId()).thenReturn(saved))
            .doOnSuccess(saved -> metrics.recordCancelled(origin));
    }

    // TC-25: cambio de estado para cocina/ADMIN; la regla vive en OrderStateService.
    public Mono<TacoOrder> updateStatus(String orderId, OrderStatus target, String reason, Actor actor) {
        return orderRepo.findById(orderId)
            .switchIfEmpty(Mono.error(() -> orderNotFound()))
            .flatMap(order -> {
                if (target == OrderStatus.CANCELLED) {
                    return applyCancellation(order, actor, reason, KitchenService.ORIGIN);
                }
                if (!stateService.transition(order, target, actor, KitchenService.ORIGIN, reason)) {
                    return Mono.just(order);
                }
                return transactionalOrderService.saveOrderWithEvent(order, OrderEventType.STATUS_CHANGED);
            });
    }

    private Mono<TacoOrder> findForOperation(String orderId, Actor actor) {
        return orderRepo.findById(orderId)
            .switchIfEmpty(Mono.error(() -> orderNotFound()))
            .flatMap(order -> actor.isAdmin() || actor.getUserId().equals(order.getUserId())
                ? Mono.just(order)
                : Mono.error(new ForbiddenException("ORDER_ACCESS_DENIED", "The order belongs to another user.")));
    }

    private static NotFoundException orderNotFound() {
        return new NotFoundException("ORDER_NOT_FOUND", "Order not found.");
    }
}
