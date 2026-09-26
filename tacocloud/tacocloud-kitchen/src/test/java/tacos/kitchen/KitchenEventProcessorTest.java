package tacos.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderEventPayload;
import tacos.messaging.contract.OrderEventType;

/**
 * TC-30: consumidor idempotente contra MongoDB real (embebido en la prueba).
 * La llave es eventId: el mismo evento entregado dos veces cambia el estado una sola vez.
 */
@DataMongoTest(properties = "spring.mongodb.embedded.version=4.0.12")
public class KitchenEventProcessorTest {

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private ProcessedEventRepository processedEvents;

    private SimpleMeterRegistry registry;
    private KitchenEventProcessor processor;

    @BeforeEach
    void setUp() {
        mongo.dropCollection(KitchenTicket.class);
        mongo.dropCollection(ProcessedEvent.class);
        registry = new SimpleMeterRegistry();
        processor = new KitchenEventProcessor(mongo, processedEvents, registry, Clock.systemUTC());
    }

    public static OrderEvent event(String eventId, OrderEventType type, String status) {
        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId("order-1");
        payload.setStatus(status);
        payload.setItems(Arrays.asList(new OrderEventPayload.OrderItemPayload("Classic",
            Arrays.asList("Flour Tortilla"), 2)));
        OrderEvent event = new OrderEvent();
        event.setEventId(eventId);
        event.setEventType(type);
        event.setCorrelationId("cid-1");
        event.setPayload(payload);
        return event;
    }

    @Test
    void sameEventDeliveredTwiceChangesStateOnce() {
        OrderEvent created = event("e-1", OrderEventType.ORDER_CREATED, "CREATED");

        assertThat(processor.process(created)).isEqualTo(KitchenEventProcessor.Result.PROCESSED);
        assertThat(processor.process(created)).isEqualTo(KitchenEventProcessor.Result.DUPLICATE);

        KitchenTicket ticket = mongo.findById("order-1", KitchenTicket.class);
        assertThat(ticket.getProcessedEventIds()).containsExactly("e-1");
        assertThat(processedEvents.count()).isEqualTo(1);
        assertThat(registry.counter("tacocloud.kitchen.events", "result", "duplicate").count()).isEqualTo(1.0);
    }

    @Test
    void statusChangesAreAppliedInOrderAndOlderCreateDoesNotRegress() {
        processor.process(event("e-2", OrderEventType.STATUS_CHANGED, "ACCEPTED"));
        processor.process(event("e-1", OrderEventType.ORDER_CREATED, "CREATED"));

        KitchenTicket ticket = mongo.findById("order-1", KitchenTicket.class);
        assertThat(ticket.getStatus()).isEqualTo("ACCEPTED");
        assertThat(ticket.getItems()).hasSize(1);
    }

    // Crash entre el efecto y el registro/ack: la reentrega completa el registro sin repetir el efecto.
    @Test
    void redeliveryAfterCrashBetweenEffectAndAckDoesNotDuplicateEffects() {
        OrderEvent accepted = event("e-3", OrderEventType.STATUS_CHANGED, "ACCEPTED");
        processor.process(accepted);
        processedEvents.deleteById("e-3"); // el proceso murió antes de registrar ProcessedEvent

        assertThat(processor.process(accepted)).isEqualTo(KitchenEventProcessor.Result.DUPLICATE);
        assertThat(processedEvents.existsById("e-3")).isTrue();
        assertThat(mongo.findById("order-1", KitchenTicket.class).getProcessedEventIds()).containsExactly("e-3");
    }

    @Test
    void replayOfAlreadyProcessedEventsDoesNotDuplicateEffects() {
        OrderEvent created = event("e-1", OrderEventType.ORDER_CREATED, "CREATED");
        OrderEvent cancelled = event("e-4", OrderEventType.CANCELLED, "CANCELLED");
        processor.process(created);
        processor.process(cancelled);

        processor.process(created);
        processor.process(cancelled);

        KitchenTicket ticket = mongo.findById("order-1", KitchenTicket.class);
        assertThat(ticket.getStatus()).isEqualTo("CANCELLED");
        assertThat(ticket.getProcessedEventIds()).containsExactlyInAnyOrder("e-1", "e-4");
    }

    @Test
    void unknownVersionOrIncompleteEventIsRejectedExplicitlyWithoutEffects() {
        OrderEvent v2 = event("e-9", OrderEventType.ORDER_CREATED, "CREATED");
        v2.setVersion("v2");
        OrderEvent noType = event("e-10", null, "CREATED");

        assertThatThrownBy(() -> processor.process(v2)).isInstanceOf(UnsupportedEventException.class);
        assertThatThrownBy(() -> processor.process(noType)).isInstanceOf(UnsupportedEventException.class);
        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), KitchenTicket.class)).isZero();
        assertThat(processedEvents.count()).isZero();
    }
}
