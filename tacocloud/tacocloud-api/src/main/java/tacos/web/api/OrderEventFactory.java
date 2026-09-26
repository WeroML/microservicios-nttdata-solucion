package tacos.web.api;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tacos.Ingredient;
import tacos.TacoOrder;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderEventPayload;
import tacos.messaging.contract.OrderEventType;

/**
 * TC-27/TC-31: construye el evento del contrato a partir de la orden.
 * Sólo copia datos seguros: nada de pago, usuario ni dirección completa.
 * El correlationId llega explícito (desde el Reactor Context de la petición);
 * si no hay uno, se genera un UUID para que siempre esté presente.
 */
@Component
public class OrderEventFactory {

    private final Clock clock;

    public OrderEventFactory(Clock clock) {
        this.clock = clock;
    }

    public OrderEvent create(TacoOrder order, OrderEventType type, String correlationId) {
        OrderEvent event = new OrderEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(type);
        event.setVersion(OrderEvent.CURRENT_VERSION);
        event.setOccurredAt(Instant.now(clock));
        event.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        event.setPayload(payload(order));
        return event;
    }

    private OrderEventPayload payload(TacoOrder order) {
        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getId());
        payload.setStatus(order.getStatus().name());
        payload.setDeliveryCity(order.getDeliveryCity());
        payload.setDeliveryState(order.getDeliveryState());
        payload.setTotal(order.getTotal());
        payload.setCurrency(order.getCurrency());
        payload.setItems(order.getItems().stream().map(item -> new OrderEventPayload.OrderItemPayload(
                item.getTaco().getName(),
                item.getTaco().getIngredients().stream().map(Ingredient::getName).collect(Collectors.toList()),
                item.getQuantity()))
            .collect(Collectors.toList()));
        return payload;
    }
}
