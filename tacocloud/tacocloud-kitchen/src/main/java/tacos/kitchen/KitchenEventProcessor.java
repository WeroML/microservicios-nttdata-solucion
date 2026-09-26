package tacos.kitchen;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import tacos.messaging.contract.OrderEvent;

/**
 * TC-30: consumidor idempotente. La llave es eventId (no orderId: una orden
 * genera varios eventos).
 *
 * 1. Se aplica el cambio en el ticket con una sola escritura condicionada a que
 *    el eventId no esté en processedEventIds (efecto + marca, atómico).
 * 2. Se guarda ProcessedEvent (índice único por eventId) como registro de auditoría.
 * Si el proceso muere entre 1 y 2, o antes del ack, el broker reentrega: el paso 1
 * no repite el efecto y el paso 2 se completa. Un duplicado se confirma sin repetir negocio.
 */
@Service
public class KitchenEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(KitchenEventProcessor.class);

    public enum Result { PROCESSED, DUPLICATE }

    private final MongoTemplate mongo;
    private final ProcessedEventRepository processedEvents;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public KitchenEventProcessor(MongoTemplate mongo, ProcessedEventRepository processedEvents,
                                 MeterRegistry meterRegistry, Clock clock) {
        this.mongo = mongo;
        this.processedEvents = processedEvents;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public Result process(OrderEvent event) {
        validate(event);
        MDC.put("correlationId", event.getCorrelationId());
        try {
            Result result = applyEffect(event) ? Result.PROCESSED : Result.DUPLICATE;
            recordProcessed(event, result);
            meterRegistry.counter("tacocloud.kitchen.events", "result", result.name().toLowerCase()).increment();
            log.info("Event {} ({}) for order {}: {}", event.getEventId(), event.getEventType(),
                event.getPayload().getOrderId(), result);
            return result;
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static void validate(OrderEvent event) {
        if (!OrderEvent.CURRENT_VERSION.equals(event.getVersion())) {
            throw new UnsupportedEventException("Unsupported event version: " + event.getVersion());
        }
        if (event.getEventType() == null || event.getEventId() == null
            || event.getPayload() == null || event.getPayload().getOrderId() == null) {
            throw new UnsupportedEventException("Incomplete or unknown order event");
        }
    }

    private boolean applyEffect(OrderEvent event) {
        Instant now = Instant.now(clock);
        Query notYetApplied = Query.query(where("_id").is(event.getPayload().getOrderId())
            .and("processedEventIds").ne(event.getEventId()));
        Update update = new Update()
            .addToSet("processedEventIds", event.getEventId())
            .set("updatedAt", now)
            .setOnInsert("receivedAt", now);

        switch (event.getEventType()) {
            case ORDER_CREATED:
                update.set("items", event.getPayload().getItems());
                // Si ya llegó un cambio de estado posterior, no se regresa a CREATED.
                update.setOnInsert("status", event.getPayload().getStatus());
                break;
            case STATUS_CHANGED:
            case CANCELLED:
                update.set("status", event.getPayload().getStatus());
                break;
            default:
                throw new UnsupportedEventException("Unknown event type: " + event.getEventType());
        }

        try {
            mongo.upsert(notYetApplied, update, KitchenTicket.class);
            return true;
        } catch (DuplicateKeyException alreadyApplied) {
            // El ticket existe y ya contiene este eventId: el filtro no coincidió y el upsert chocó con el _id.
            return false;
        }
    }

    private void recordProcessed(OrderEvent event, Result result) {
        if (processedEvents.existsById(event.getEventId())) {
            return;
        }
        try {
            processedEvents.save(new ProcessedEvent(event.getEventId(), event.getPayload().getOrderId(),
                event.getEventType().name(), result.name(), Instant.now(clock)));
        } catch (DuplicateKeyException concurrentDuplicate) {
            // Otro consumidor lo registró al mismo tiempo; el efecto ya es idempotente.
        }
    }
}
