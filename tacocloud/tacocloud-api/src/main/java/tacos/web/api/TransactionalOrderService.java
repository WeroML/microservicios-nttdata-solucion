package tacos.web.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.OutboxEvent;
import tacos.data.OrderRepository;
import tacos.data.OutboxEventRepository;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderEventPayload;
import tacos.messaging.contract.OrderEventType;
import java.util.stream.Collectors;

@Service
public class TransactionalOrderService {

    private final OrderRepository orderRepo;
    private final OutboxEventRepository outboxRepo;
    private final TacoMetricsService metricsService;

    public TransactionalOrderService(OrderRepository orderRepo, OutboxEventRepository outboxRepo, TacoMetricsService metricsService) {
        this.orderRepo = orderRepo;
        this.outboxRepo = outboxRepo;
        this.metricsService = metricsService;
    }

    @Transactional
    public Mono<TacoOrder> saveOrderAndOutboxEvent(TacoOrder order) {
        return orderRepo.save(order)
            .flatMap(savedOrder -> {
                OrderEvent orderEvent = new OrderEvent();
                orderEvent.setEventType(OrderEventType.ORDER_CREATED);
                // orderEvent.setCorrelationId(savedOrder.getId());

                OrderEventPayload payload = new OrderEventPayload();
                payload.setOrderId(savedOrder.getId());
                payload.setUserId(savedOrder.getUser() != null ? savedOrder.getUser().getId() : null);
                payload.setDeliveryCity(savedOrder.getDeliveryCity());
                payload.setDeliveryState(savedOrder.getDeliveryState());
                payload.setDeliveryZip(savedOrder.getDeliveryZip());
                payload.setStatus(savedOrder.getStatus() != null ? savedOrder.getStatus().name() : "CREATED");
                payload.setTotal(savedOrder.getTotal());

                if (savedOrder.getItems() != null) {
                    payload.setItems(savedOrder.getItems().stream().map(item -> {
                        OrderEventPayload.OrderItemPayload ip = new OrderEventPayload.OrderItemPayload();
                        ip.setQuantity(item.getQuantity());
                        if (item.getTaco() != null) {
                            ip.setTacoName(item.getTaco().getName());
                            if (item.getTaco().getIngredients() != null) {
                                ip.setIngredients(item.getTaco().getIngredients().stream().map(i -> i.getName()).collect(Collectors.toList()));
                            }
                        }
                        return ip;
                    }).collect(Collectors.toList()));
                }
                
                orderEvent.setPayload(payload);

                OutboxEvent outboxEvent = new OutboxEvent();
                outboxEvent.setId(orderEvent.getEventId());
                outboxEvent.setAggregateId(savedOrder.getId());
                outboxEvent.setAggregateType("TacoOrder");
                outboxEvent.setType(OrderEventType.ORDER_CREATED.name());
                outboxEvent.setPayload(orderEvent);

                return Mono.deferContextual(ctx -> {
                    String correlationId = ctx.getOrDefault(CorrelationIdFilter.CORRELATION_ID_KEY, orderEvent.getEventId());
                    orderEvent.setCorrelationId(correlationId);
                    return outboxRepo.save(outboxEvent).thenReturn(savedOrder);
                });
            })
            .doOnSuccess(o -> metricsService.recordOrderCreated(o.getStatus() != null ? o.getStatus().name() : "CREATED"))
            .doOnError(e -> metricsService.recordOrderFailed("CREATED"));
    }

    @Transactional
    public Mono<TacoOrder> saveOrderAndStatusEvent(TacoOrder order, OrderEventType eventType) {
        return orderRepo.save(order)
            .flatMap(savedOrder -> {
                OrderEvent orderEvent = new OrderEvent();
                orderEvent.setEventType(eventType);
                // correlationId will be set from Context

                OrderEventPayload payload = new OrderEventPayload();
                payload.setOrderId(savedOrder.getId());
                payload.setUserId(savedOrder.getUser() != null ? savedOrder.getUser().getId() : null);
                payload.setStatus(savedOrder.getStatus() != null ? savedOrder.getStatus().name() : "CREATED");
                
                orderEvent.setPayload(payload);

                OutboxEvent outboxEvent = new OutboxEvent();
                outboxEvent.setId(orderEvent.getEventId());
                outboxEvent.setAggregateId(savedOrder.getId());
                outboxEvent.setAggregateType("TacoOrder");
                outboxEvent.setType(eventType.name());
                outboxEvent.setPayload(orderEvent);

                return Mono.deferContextual(ctx -> {
                    String correlationId = ctx.getOrDefault(CorrelationIdFilter.CORRELATION_ID_KEY, orderEvent.getEventId());
                    orderEvent.setCorrelationId(correlationId);
                    return outboxRepo.save(outboxEvent).thenReturn(savedOrder);
                });
            });
    }
}
