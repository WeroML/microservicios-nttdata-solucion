package tacos.web.api;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;
import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.data.OrderRepository;
import tacos.data.OutboxEventRepository;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderEventType;

/**
 * TC-29: guardar la orden y registrar su evento en el outbox es una sola decisión
 * local (transacción Mongo; requiere replica set). Aquí no se publica nada:
 * OutboxPublisher entrega después, al menos una vez.
 */
@Service
public class TransactionalOrderService {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOrderService.class);

    private final OrderRepository orderRepo;
    private final OutboxEventRepository outboxRepo;
    private final OrderEventFactory eventFactory;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public TransactionalOrderService(OrderRepository orderRepo, OutboxEventRepository outboxRepo,
                                     OrderEventFactory eventFactory, IdempotencyService idempotencyService,
                                     Clock clock) {
        this.orderRepo = orderRepo;
        this.outboxRepo = outboxRepo;
        this.eventFactory = eventFactory;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    // Orden nueva + ORDER_CREATED (+ registro de idempotencia COMPLETED) en la misma transacción.
    @Transactional
    public Mono<TacoOrder> saveNewOrder(TacoOrder order, IdempotencyService.Key idempotencyKey) {
        return saveWithEvent(order, OrderEventType.ORDER_CREATED)
            .flatMap(saved -> idempotencyKey == null
                ? Mono.just(saved)
                : idempotencyService.complete(idempotencyKey.getUserId(), idempotencyKey.getValue(), saved.getId())
                    .thenReturn(saved));
    }

    @Transactional
    public Mono<TacoOrder> saveOrderWithEvent(TacoOrder order, OrderEventType eventType) {
        return saveWithEvent(order, eventType);
    }

    // Registra sólo el evento (la orden ya se modificó atómicamente, p. ej. el claim de cocina).
    public Mono<OutboxEvent> registerEvent(TacoOrder order, OrderEventType eventType) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationIdFilter.CORRELATION_ID_KEY, null);
            OrderEvent event = eventFactory.create(order, eventType, correlationId);
            return outboxRepo.save(toOutbox(order, event))
                .doOnSuccess(saved -> logRegistered(order, event));
        });
    }

    private Mono<TacoOrder> saveWithEvent(TacoOrder order, OrderEventType eventType) {
        return orderRepo.save(order)
            .flatMap(saved -> registerEvent(saved, eventType).thenReturn(saved));
    }

    private OutboxEvent toOutbox(TacoOrder order, OrderEvent event) {
        Instant now = Instant.now(clock);
        OutboxEvent outbox = new OutboxEvent();
        outbox.setId(event.getEventId());
        outbox.setAggregateId(order.getId());
        outbox.setAggregateType("TacoOrder");
        outbox.setType(event.getEventType().name());
        outbox.setEventVersion(event.getVersion());
        outbox.setPayload(event);
        outbox.setStatus(OutboxEvent.OutboxStatus.NEW);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        return outbox;
    }

    // El log lleva el mismo correlationId que el evento, aunque el hilo sea otro.
    private static void logRegistered(TacoOrder order, OrderEvent event) {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, event.getCorrelationId());
        try {
            log.info("Order {} registered outbox event {} ({})", order.getId(), event.getEventId(), event.getEventType());
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);
        }
    }
}
