package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.messaging.contract.OrderEvent;

// TC-04, TC-05, TC-14, TC-23, TC-24, TC-25, TC-31, TC-34 de punta a punta.
class OrderFlowIT extends IntegrationTestBase {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode create(String username, String paymentId) throws Exception {
        byte[] body = postOrder(username, orderJson(paymentId, 2, "FLTO", "GRBF", "CHED"), null)
            .expectStatus().isCreated().expectBody().returnResult().getResponseBody();
        return json.readTree(body);
    }

    @Test
    void orderTotalsAreCalculatedByTheServer() throws Exception {
        String body = orderJson(customerPaymentId("habuma"), 2, "FLTO", "GRBF", "CHED")
            .replace("{\"deliveryName\"", "{\"total\":0.01,\"status\":\"DELIVERED\",\"deliveryName\"");

        postOrder("habuma", body, null).expectStatus().isCreated().expectBody()
            .jsonPath("$.items[0].unitPriceAtPurchase").isEqualTo(2.35)
            .jsonPath("$.items[0].subtotal").isEqualTo(4.70)
            .jsonPath("$.total").isEqualTo(4.70)
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.currency").isEqualTo("USD")
            .jsonPath("$.payment.last4").isEqualTo("1111");
    }

    @Test
    void historyIsPrivateNewestFirstAndSafe() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        JsonNode first = create(alice[0], alice[1]);
        JsonNode second = create(alice[0], alice[1]);
        JsonNode bobs = create(bob[0], bob[1]);

        as(alice[0], client.get().uri("/api/v1/users/me/orders")).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].id").isEqualTo(second.get("id").asText())
            .jsonPath("$.content[1].id").isEqualTo(first.get("id").asText());
        as(alice[0], client.get().uri("/api/v1/users/me/orders/" + bobs.get("id").asText()))
            .exchange().expectStatus().isNotFound();
        String detail = as(alice[0], client.get().uri("/api/v1/users/me/orders/" + first.get("id").asText()))
            .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertThat(detail).doesNotContain("password", "tok_", "userId", "ccNumber");
        as(alice[0], client.get().uri("/api/v1/users/me/orders?page=5&size=10")).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    void patchPutAndDeleteRespectOwnership() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String id = create(alice[0], alice[1]).get("id").asText();

        as(bob[0], client.patch().uri("/api/v1/orders/" + id).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"deliveryZip\":\"99999\"}"))
            .exchange().expectStatus().isForbidden();
        as(alice[0], client.patch().uri("/api/v1/orders/" + id).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"deliveryZip\":\"99999\"}"))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.deliveryZip").isEqualTo("99999").jsonPath("$.deliveryState").isEqualTo("TX");
        as(alice[0], client.patch().uri("/api/v1/orders/" + id).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"total\":0}"))
            .exchange().expectStatus().isBadRequest();

        as(bob[0], client.delete().uri("/api/v1/orders/" + id)).exchange().expectStatus().isForbidden();
        as(alice[0], client.delete().uri("/api/v1/orders/" + id)).exchange().expectStatus().isNoContent();
        TacoOrder cancelled = mongo.findById(id, TacoOrder.class).block();
        assertThat(cancelled.getStatus().name()).isEqualTo("CANCELLED");
        as(alice[0], client.delete().uri("/api/v1/orders/missing-order")).exchange().expectStatus().isNotFound();
    }

    @Test
    void putUsesThePathIdAndCannotRedirectTheWrite() throws Exception {
        String[] alice = newCustomer();
        String first = create(alice[0], alice[1]).get("id").asText();
        String other = create(alice[0], alice[1]).get("id").asText();
        String replacement = orderJson(alice[1], 1, "COTO", "TMTO").replace("{\"deliveryName\"",
            "{\"id\":\"" + other + "\",\"deliveryName\"");

        as(alice[0], client.put().uri("/api/v1/orders/" + first).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(replacement))
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(first)
            .jsonPath("$.total").isEqualTo(0.75);
        assertThat(mongo.findById(other, TacoOrder.class).block().getTotal()).isEqualByComparingTo("4.70");
    }

    @Test
    void statusFlowRolesAndHistory() throws Exception {
        String[] alice = newCustomer();
        String id = create(alice[0], alice[1]).get("id").asText();

        as(alice[0], client.patch().uri("/api/v1/orders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACCEPTED\"}"))
            .exchange().expectStatus().isForbidden();
        for (String status : new String[] {"ACCEPTED", "PREPARING", "READY"}) {
            as("kitchen", client.patch().uri("/api/v1/orders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"status\":\"" + status + "\"}"))
                .exchange().expectStatus().isOk();
        }
        as("kitchen", client.patch().uri("/api/v1/orders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"DELIVERED\"}"))
            .exchange().expectStatus().isEqualTo(409);
        as(alice[0], client.post().uri("/api/v1/orders/" + id + "/cancel")).exchange().expectStatus().isEqualTo(409);

        as(alice[0], client.get().uri("/api/v1/users/me/orders/" + id)).exchange().expectBody()
            .jsonPath("$.statusHistory.length()").isEqualTo(4)
            .jsonPath("$.statusHistory[0].status").isEqualTo("CREATED")
            .jsonPath("$.statusHistory[1].status").isEqualTo("ACCEPTED")
            .jsonPath("$.statusHistory[2].status").isEqualTo("PREPARING")
            .jsonPath("$.statusHistory[3].status").isEqualTo("READY")
            .jsonPath("$.statusHistory[1].changedBy").isEqualTo("kitchen");
    }

    @Test
    void staleVersionIsDetectedAsConflict() throws Exception {
        String[] alice = newCustomer();
        String id = create(alice[0], alice[1]).get("id").asText();
        TacoOrder copyA = mongo.findById(id, TacoOrder.class).block();
        TacoOrder copyB = mongo.findById(id, TacoOrder.class).block();

        copyA.setDeliveryName("First writer");
        mongo.save(copyA).block();
        copyB.setDeliveryName("Second writer");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mongo.save(copyB).block())
            .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    void correlationIdTravelsFromHttpToTheEvent() throws Exception {
        String[] alice = newCustomer();
        byte[] body = client.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBasicAuth(alice[0], PASSWORD)).header("X-Correlation-Id", "it-corr-123")
            .bodyValue(orderJson(alice[1], 1, "FLTO", "GRBF")).exchange()
            .expectStatus().isCreated()
            .expectHeader().valueEquals("X-Correlation-Id", "it-corr-123")
            .expectBody().returnResult().getResponseBody();
        String orderId = json.readTree(body).get("id").asText();

        OutboxEvent outbox = mongo.findOne(Query.query(where("aggregateId").is(orderId)), OutboxEvent.class).block();
        assertThat(((OrderEvent) outbox.getPayload()).getCorrelationId()).isEqualTo("it-corr-123");

        client.get().uri("/api/v1/tacos/does-not-exist").header("X-Correlation-Id", "bad value;<script>")
            .exchange().expectStatus().isNotFound()
            .expectHeader().value("X-Correlation-Id", v -> assertThat(v).doesNotContain("bad"));
    }

    // ---- TC-34 ----

    @Test
    void sameKeySequentialCreatesOneOrder() {
        String[] alice = newCustomer();
        String body = orderJson(alice[1], 1, "FLTO", "GRBF");

        String first = postOrder(alice[0], body, "seq-key-0001").expectStatus().isCreated()
            .expectBody(String.class).returnResult().getResponseBody();
        String second = postOrder(alice[0], body, "seq-key-0001").expectStatus().isOk()
            .expectBody(String.class).returnResult().getResponseBody();

        assertThat(second).contains(first.substring(first.indexOf("\"id\""), first.indexOf("\"placedAt\"")));
        assertThat(count(TacoOrder.class)).isEqualTo(1);
        assertThat(stockOf("GRBF")).isEqualTo(99);
        assertThat(count(OutboxEvent.class)).isEqualTo(1);
    }

    @Test
    void sameKeyConcurrentCreatesOneOrder() throws Exception {
        String[] alice = newCustomer();
        String body = orderJson(alice[1], 1, "FLTO", "GRBF");
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return postOrder(alice[0], body, "conc-key-0001").returnResult(String.class).getStatus().value();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (CompletableFuture<Integer> call : calls) {
            statuses.add(call.get());
        }

        assertThat(statuses).isSubsetOf(201, 200, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(count(TacoOrder.class)).isEqualTo(1);
        assertThat(stockOf("GRBF")).isEqualTo(99);
    }

    @Test
    void sameKeyDifferentPayloadIs409AndKeysAreScopedPerUser() {
        String[] alice = newCustomer();
        String[] bob = newCustomer();

        postOrder(alice[0], orderJson(alice[1], 1, "FLTO", "GRBF"), "shared-key-01").expectStatus().isCreated();
        postOrder(alice[0], orderJson(alice[1], 2, "FLTO", "GRBF"), "shared-key-01").expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        postOrder(bob[0], orderJson(bob[1], 1, "FLTO", "GRBF"), "shared-key-01").expectStatus().isCreated();
        assertThat(count(TacoOrder.class)).isEqualTo(2);
    }

    // ---- TC-24 ----

    @Test
    void reorderCreatesAFreshOrderWithCurrentPrices() throws Exception {
        String[] alice = newCustomer();
        String original = create(alice[0], alice[1]).get("id").asText();
        as("admin", client.patch().uri("/api/v1/admin/ingredients/GRBF/catalog").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"unitPrice\":2.00}"))
            .exchange().expectStatus().isOk();
        String reorder = "{\"paymentMethodId\":\"" + alice[1] + "\",\"confirmPriceChange\":false}";

        as(alice[0], client.post().uri("/api/v1/orders/" + original + "/reorder").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reorder))
            .exchange().expectStatus().isEqualTo(409).expectBody()
            .jsonPath("$.confirmed").isEqualTo(false)
            .jsonPath("$.order.total").isEqualTo(6.20)
            .jsonPath("$.differences.length()").isEqualTo(2);
        as(alice[0], client.post().uri("/api/v1/orders/" + original + "/reorder").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reorder.replace("false", "true")))
            .exchange().expectStatus().isCreated().expectBody()
            .jsonPath("$.confirmed").isEqualTo(true)
            .jsonPath("$.order.status").isEqualTo("CREATED")
            .jsonPath("$.order.id").value(id -> assertThat(id).isNotEqualTo(original));

        TacoOrder untouched = mongo.findById(original, TacoOrder.class).block();
        assertThat(untouched.getTotal()).isEqualByComparingTo("4.70");
        assertThat(untouched.getItems().get(0).getUnitPriceAtPurchase()).isEqualByComparingTo("2.35");
        as("admin", client.patch().uri("/api/v1/admin/ingredients/GRBF/catalog").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"unitPrice\":1.25}"))
            .exchange().expectStatus().isOk();
    }

    @Test
    void foreignOrderCannotBeReordered() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        String original = create(alice[0], alice[1]).get("id").asText();

        as(bob[0], client.post().uri("/api/v1/orders/" + original + "/reorder").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"paymentMethodId\":\"" + bob[1] + "\"}"))
            .exchange().expectStatus().isNotFound();
    }
}
