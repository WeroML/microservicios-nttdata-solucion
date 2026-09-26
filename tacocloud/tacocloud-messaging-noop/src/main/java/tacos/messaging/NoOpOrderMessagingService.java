package tacos.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

/**
 * TC-28: adaptador para desarrollo y pruebas. Sólo se activa si la propiedad lo
 * pide explícitamente (sin matchIfMissing), y avisa al arrancar para que nunca
 * quede activo en silencio.
 */
@Service
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="noop")
@Slf4j
public class NoOpOrderMessagingService implements OrderMessagingService {

    public NoOpOrderMessagingService() {
        log.warn("NoOp messaging transport is active: order events are NOT delivered to any broker.");
    }

    // Sólo IDs: nunca el payload completo en el log.
    @Override
    public void sendOrderEvent(OrderEvent event) {
        log.info("No-op messaging: discarded event {} ({})", event.getEventId(), event.getEventType());
    }

}
