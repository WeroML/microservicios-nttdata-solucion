package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderEventType;

// TC-26: cola FIFO, claim atómico, ETA y DTO seguro.
class KitchenIT extends IntegrationTestBase {

    private List<String> placeOrders(int count) {
        String[] alice = newCustomer();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String body = postOrder(alice[0], orderJson(alice[1], i + 1, "FLTO", "GRBF"), null)
                .expectStatus().isCreated().expectBody(String.class).returnResult().getResponseBody();
            ids.add(body.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1"));
        }
        return ids;
    }

    @Test
    void queueIsFifoWithGrowingEtaAndSafeFields() {
        List<String> ids = placeOrders(2);

        String json = as("kitchen", client.get().uri("/api/v1/kitchen/queue")).exchange()
            .expectStatus().isOk().expectBody()
            .jsonPath("$[0].id").isEqualTo(ids.get(0))
            .jsonPath("$[1].id").isEqualTo(ids.get(1))
            .returnResult().toString();
        // ETA: 5 + 2*1 + 1*2 + 3*0 = 9 y 5 + 2*2 + 1*2 + 3*1 = 14
        as("kitchen", client.get().uri("/api/v1/kitchen/queue")).exchange().expectBody()
            .jsonPath("$[0].estimatedPrepMinutes").isEqualTo(9)
            .jsonPath("$[1].estimatedPrepMinutes").isEqualTo(14);
        assertThat(json).doesNotContain("deliveryStreet", "payment", "userId", "total");
    }

    @Test
    void concurrentClaimsNeverTakeTheSameOrder() throws Exception {
        placeOrders(1);
        List<CompletableFuture<Integer>> claims = new ArrayList<>();
        for (String cook : new String[] {"kitchen", "kitchen2"}) {
            ensureCook(cook);
            claims.add(CompletableFuture.supplyAsync(() -> as(cook, client.post().uri("/api/v1/kitchen/orders/claim"))
                .exchange().returnResult(String.class).getStatus().value()));
        }
        List<Integer> statuses = new ArrayList<>();
        for (CompletableFuture<Integer> claim : claims) {
            statuses.add(claim.get());
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 204);
    }

    @Test
    void claimedOrderIsAcceptedWithStationCookAndEvent() {
        String id = placeOrders(1).get(0);

        as("kitchen", client.post().uri("/api/v1/kitchen/orders/claim")).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.id").isEqualTo(id).jsonPath("$.status").isEqualTo("ACCEPTED")
            .jsonPath("$.cookId").isEqualTo("kitchen").jsonPath("$.stationId").isEqualTo("STATION-1");

        TacoOrder claimed = mongo.findById(id, TacoOrder.class).block();
        assertThat(claimed.getStatusHistory()).hasSize(2);
        List<OutboxEvent> events = mongo.find(Query.query(where("aggregateId").is(id)), OutboxEvent.class)
            .collectList().block();
        assertThat(events).extracting(e -> ((OrderEvent) e.getPayload()).getEventType())
            .contains(OrderEventType.STATUS_CHANGED);
    }

    @Test
    void stationCannotClaimTwiceWhileItHasAnAcceptedOrder() {
        placeOrders(2);

        as("kitchen", client.post().uri("/api/v1/kitchen/orders/claim")).exchange().expectStatus().isOk();
        as("kitchen", client.post().uri("/api/v1/kitchen/orders/claim")).exchange().expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo("STATION_BUSY");
    }

    private void ensureCook(String username) {
        if (userRepo.findByUsername(username).block() == null) {
            tacos.User cook = new tacos.User(username, encoder.encode(PASSWORD), "Cook", "", "", "", "", "",
                username + "@tacocloud.test");
            cook.setRoles(new java.util.HashSet<>(java.util.Collections.singleton(tacos.User.ROLE_KITCHEN)));
            userRepo.save(cook).block();
        }
    }
}
