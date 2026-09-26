package tacos.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// TC-27: pruebas de compatibilidad del contrato JSON v1.
class OrderEventContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private OrderEvent sampleEvent() {
        OrderEventPayload payload = new OrderEventPayload("order-1", "CREATED", "Cross Roads", "TX",
            new BigDecimal("4.23"), "USD",
            Arrays.asList(new OrderEventPayload.OrderItemPayload("Carnivore",
                Arrays.asList("Flour Tortilla", "Ground Beef"), 2)));
        OrderEvent event = new OrderEvent();
        event.setEventType(OrderEventType.ORDER_CREATED);
        event.setCorrelationId("cid-123");
        event.setPayload(payload);
        return event;
    }

    @Test
    void serializesAndDeserializesVersion1() throws Exception {
        OrderEvent event = sampleEvent();

        String json = mapper.writeValueAsString(event);
        OrderEvent read = mapper.readValue(json, OrderEvent.class);

        assertThat(read).isEqualTo(event);
        assertThat(read.getVersion()).isEqualTo("v1");
    }

    @Test
    void eventIdCorrelationIdAndVersionAreAlwaysPresent() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(sampleEvent()));

        assertThat(json.get("eventId").asText()).isNotBlank();
        assertThat(json.get("correlationId").asText()).isNotBlank();
        assertThat(json.get("version").asText()).isEqualTo(OrderEvent.CURRENT_VERSION);
    }

    @Test
    void eventIdIsAUniqueUuid() {
        OrderEvent first = new OrderEvent();
        OrderEvent second = new OrderEvent();

        assertThat(UUID.fromString(first.getEventId()).toString()).isEqualTo(first.getEventId());
        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    @Test
    void v1ConsumerIgnoresCompatibleAdditionalFields() throws Exception {
        String json = "{\"eventId\":\"e-1\",\"eventType\":\"STATUS_CHANGED\",\"version\":\"v1\","
            + "\"correlationId\":\"c-1\",\"newTopLevelField\":42,"
            + "\"payload\":{\"orderId\":\"o-1\",\"status\":\"READY\",\"newPayloadField\":\"x\","
            + "\"items\":[{\"tacoName\":\"T\",\"quantity\":1,\"newItemField\":true}]}}";

        OrderEvent read = mapper.readValue(json, OrderEvent.class);

        assertThat(read.getPayload().getStatus()).isEqualTo("READY");
        assertThat(read.getPayload().getItems()).hasSize(1);
    }

    @Test
    void jsonHasNoSensitiveFields() throws Exception {
        String json = mapper.writeValueAsString(sampleEvent()).toLowerCase();

        assertThat(json).doesNotContain("ccnumber", "cccvv", "cvv", "pan", "password",
            "paymenttoken", "user", "street");
    }

    // Snapshot: si alguien agrega, quita o renombra un campo, esta prueba lo detecta.
    @Test
    void snapshotOfV1Fields() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(sampleEvent()));

        assertThat(fieldNames(json)).containsExactly(
            "correlationId", "eventId", "eventType", "occurredAt", "payload", "version");
        assertThat(fieldNames(json.get("payload"))).containsExactly(
            "currency", "deliveryCity", "deliveryState", "items", "orderId", "status", "total");
        assertThat(fieldNames(json.get("payload").get("items").get(0))).containsExactly(
            "ingredients", "quantity", "tacoName");
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }
}
