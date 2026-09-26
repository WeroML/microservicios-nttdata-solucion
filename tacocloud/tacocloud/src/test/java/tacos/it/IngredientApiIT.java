package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import tacos.Ingredient;

// TC-01, TC-02, TC-03, TC-13 contra el servidor real con Mongo.
class IngredientApiIT extends IntegrationTestBase {

    private static final String NEW_INGREDIENT =
        "{\"name\":\"Pickled Onion\",\"type\":\"VEGGIES\",\"unitPrice\":0.35,\"available\":true,\"stockOnHand\":4}";

    private URI create() {
        return as("admin", client.post().uri("/api/v1/ingredients")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(NEW_INGREDIENT))
            .exchange().expectStatus().isCreated()
            .returnResult(String.class).getResponseHeaders().getLocation();
    }

    @Test
    void locationUsesTheRealPortAndCanBeFollowed() {
        URI location = create();

        assertThat(location.toString()).startsWith("http://localhost:" + port + "/api/v1/ingredients/");
        client.get().uri(location).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.name").isEqualTo("Pickled Onion");
    }

    @Test
    void putChangesWhatGetReturnsAndNeverCreates() {
        String id = create().getPath().substring("/api/v1/ingredients/".length());
        String update = "{\"id\":\"" + id + "\",\"name\":\"Red Onion\",\"type\":\"VEGGIES\",\"unitPrice\":0.40,\"available\":true}";

        as("admin", client.put().uri("/api/v1/ingredients/" + id)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(update))
            .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/ingredients/" + id).exchange()
            .expectBody().jsonPath("$.name").isEqualTo("Red Onion");

        as("admin", client.put().uri("/api/v1/ingredients/GHOST")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(update.replace(id, "GHOST")))
            .exchange().expectStatus().isNotFound();
        assertThat(mongo.findById("GHOST", Ingredient.class).block()).isNull();
    }

    @Test
    void deleteRemovesTheDocumentAndSecondDeleteIs404() {
        URI location = create();

        as("admin", client.delete().uri(location)).exchange().expectStatus().isNoContent().expectBody().isEmpty();
        client.get().uri(location).exchange().expectStatus().isNotFound();
        as("admin", client.delete().uri(location)).exchange().expectStatus().isNotFound();
    }

    @Test
    void stockCannotGoNegativeAndStaleVersionIsConflict() {
        as("admin", client.post().uri("/api/v1/admin/ingredients/CHED/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"amount\":-101}"))
            .exchange().expectStatus().isEqualTo(422);
        assertThat(stockOf("CHED")).isEqualTo(100);

        String stale = "{\"id\":\"CHED\",\"name\":\"Cheddar\",\"type\":\"CHEESE\",\"unitPrice\":0.60,"
            + "\"available\":true,\"version\":-1}";
        as("admin", client.put().uri("/api/v1/ingredients/CHED")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(stale))
            .exchange().expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
    }
}
