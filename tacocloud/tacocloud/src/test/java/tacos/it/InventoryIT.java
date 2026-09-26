package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tacos.Ingredient;
import tacos.StockReservation;
import tacos.api.error.ConflictException;
import tacos.inventory.InventoryService;

// TC-16: reservar y liberar inventario sin vender aire, con Mongo real.
class InventoryIT extends IntegrationTestBase {

    @Autowired
    private InventoryService inventory;

    private void setStock(String id, int stock) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("stockOnHand", stock),
            Ingredient.class).block();
    }

    @Test
    void twoConcurrentBuyersNeverOversellTheLastUnit() {
        setStock("TMTO", 1);

        Long successes = Flux.range(0, 2)
            .flatMap(i -> inventory.reserve("res-" + i, "order-" + i, Collections.singletonMap("TMTO", 1))
                .map(r -> 1L)
                .onErrorResume(ConflictException.class, e -> Mono.just(0L))
                .subscribeOn(Schedulers.parallel()))
            .reduce(0L, Long::sum)
            .block();

        assertThat(successes).isEqualTo(1L);
        assertThat(stockOf("TMTO")).isZero();
    }

    @Test
    void failureHalfwayReleasesWhatWasAlreadyReserved() {
        setStock("TMTO", 1);
        Map<String, Integer> quantities = new LinkedHashMap<>();
        quantities.put("CHED", 3);  // se reserva primero (orden estable por ID)
        quantities.put("TMTO", 5);  // falla: sólo hay 1

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inventory.reserve("res-x", "order-x", quantities).block())
            .isInstanceOf(ConflictException.class).hasMessageContaining("TMTO");

        assertThat(stockOf("CHED")).isEqualTo(100);
        assertThat(stockOf("TMTO")).isEqualTo(1);
        assertThat(mongo.findById("res-x", StockReservation.class).block().getStatus())
            .isEqualTo(StockReservation.Status.FAILED);
    }

    @Test
    void retryWithTheSameReservationDoesNotDecrementAgain() {
        inventory.reserve("res-1", "order-1", Collections.singletonMap("CHED", 4)).block();
        inventory.reserve("res-1", "order-1", Collections.singletonMap("CHED", 4)).block();

        assertThat(stockOf("CHED")).isEqualTo(96);
    }

    @Test
    void cancellationReleasesExactlyOnceAndStockIsNeverNegative() {
        inventory.reserve("res-2", "order-2", Collections.singletonMap("CHED", 10)).block();

        Long released = Flux.range(0, 5)
            .flatMap(i -> inventory.release("order-2").subscribeOn(Schedulers.parallel()))
            .filter(Boolean::booleanValue).count().block();

        assertThat(released).isEqualTo(1L);
        assertThat(stockOf("CHED")).isEqualTo(100);
    }

    @Test
    void cancellingAnOrderThroughTheApiReturnsItsStock() {
        String[] alice = newCustomer();
        byte[] body = postOrder(alice[0], orderJson(alice[1], 3, "FLTO", "GRBF"), null)
            .expectStatus().isCreated().expectBody().returnResult().getResponseBody();
        assertThat(stockOf("GRBF")).isEqualTo(97);
        String id = new String(body).replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");

        as(alice[0], client.post().uri("/api/v1/orders/" + id + "/cancel")).exchange().expectStatus().isOk();
        as(alice[0], client.post().uri("/api/v1/orders/" + id + "/cancel")).exchange().expectStatus().isOk();
        assertThat(stockOf("GRBF")).isEqualTo(100);
    }

    @Test
    void insufficientStockIsAConflictWithDetail() {
        String[] alice = newCustomer();
        setStock("GRBF", 1);

        postOrder(alice[0], orderJson(alice[1], 2, "FLTO", "GRBF"), null)
            .expectStatus().isEqualTo(409).expectBody()
            .jsonPath("$.code").isEqualTo("INSUFFICIENT_STOCK")
            .jsonPath("$.detail").value(d -> assertThat((String) d).contains("GRBF"));
        assertThat(stockOf("GRBF")).isEqualTo(1);
        assertThat(stockOf("FLTO")).isEqualTo(100);
    }
}
