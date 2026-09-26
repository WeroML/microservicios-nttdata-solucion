package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import tacos.Favorite;
import tacos.TacoRating;

// TC-17, TC-19, TC-20, TC-21, TC-22 con los datos semilla y Mongo real (índices incluidos).
class CatalogFeaturesIT extends IntegrationTestBase {

    @Test
    void filtersProduceTheCorrectIntersection() {
        client.get().uri("/api/v1/tacos?diet=VEGAN").exchange().expectBody()
            .jsonPath("$.content.length()").isEqualTo(1).jsonPath("$.content[0].id").isEqualTo("TACO3");
        client.get().uri("/api/v1/tacos?excludeAllergen=DAIRY").exchange().expectBody()
            .jsonPath("$.content.length()").isEqualTo(1).jsonPath("$.content[0].id").isEqualTo("TACO3");
        client.get().uri("/api/v1/tacos?ingredientId=GRBF&spice=MEDIUM").exchange().expectBody()
            .jsonPath("$.content.length()").isEqualTo(1).jsonPath("$.content[0].id").isEqualTo("TACO1");
        client.get().uri("/api/v1/tacos?diet=GLUTEN_FREE&ingredientId=CHED").exchange().expectBody()
            .jsonPath("$.content.length()").isEqualTo(1).jsonPath("$.content[0].id").isEqualTo("TACO2");
        client.get().uri("/api/v1/tacos?name=vine").exchange().expectBody()
            .jsonPath("$.content[0].name").isEqualTo("Bovine Bounty");
        client.get().uri("/api/v1/tacos?name=.*(").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void paginationIsStableAcrossPages() {
        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            client.get().uri("/api/v1/tacos?size=1&sort=name,asc&page=" + page).exchange().expectBody()
                .jsonPath("$.content[0].id").value(id -> seen.add((String) id));
        }
        assertThat(seen).containsExactly("TACO2", "TACO1", "TACO3");
        client.get().uri("/api/v1/tacos?size=1&page=2").exchange().expectBody().jsonPath("$.hasNext").isEqualTo(false);
    }

    @Test
    void classificationIsDerivedAndExposed() {
        client.get().uri("/api/v1/tacos/TACO1/classification").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.dietaryTags.length()").isEqualTo(0)
            .jsonPath("$.spiceLevel").isEqualTo("MEDIUM")
            .jsonPath("$.disclaimer").exists();
        String forged = "{\"name\":\"Forged taco\",\"ingredientIds\":[\"COTO\",\"GRBF\"],\"dietaryTags\":[\"VEGAN\"]}";
        as("habuma", client.post().uri("/api/v1/tacos").contentType(MediaType.APPLICATION_JSON).bodyValue(forged))
            .exchange().expectStatus().isCreated().expectBody()
            .jsonPath("$.dietaryTags[0]").isEqualTo("GLUTEN_FREE")
            .jsonPath("$.dietaryTags.length()").isEqualTo(1);
    }

    @Test
    void tacoOfTheDayIsStableAndSkipsUnavailableTacos() {
        String first = client.get().uri("/api/v1/tacos/today").exchange().expectStatus().isOk()
            .expectBody(String.class).returnResult().getResponseBody();
        String second = client.get().uri("/api/v1/tacos/today").exchange()
            .expectBody(String.class).returnResult().getResponseBody();
        assertThat(first).isEqualTo(second);

        as("admin", client.patch().uri("/api/v1/admin/ingredients/{id}/catalog", "GRBF")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"available\":false}"))
            .exchange().expectStatus().isOk();
        as("admin", client.patch().uri("/api/v1/admin/ingredients/{id}/catalog", "TMTO")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"available\":false}"))
            .exchange().expectStatus().isOk();
        client.get().uri("/api/v1/tacos/today").exchange().expectStatus().isNotFound()
            .expectBody().jsonPath("$.code").isEqualTo("NO_TACO_OF_THE_DAY");
    }

    @Test
    void favoritesAreIdempotentPrivateAndUnique() throws Exception {
        String[] alice = newCustomer();
        String[] bob = newCustomer();
        List<CompletableFuture<Integer>> puts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            puts.add(CompletableFuture.supplyAsync(() -> as(alice[0],
                client.put().uri("/api/v1/users/me/favorites/TACO1")).exchange().returnResult(Void.class)
                .getStatus().value()));
        }
        for (CompletableFuture<Integer> put : puts) {
            assertThat(put.get()).isEqualTo(204);
        }
        assertThat(count(Favorite.class)).isEqualTo(1);

        as(alice[0], client.get().uri("/api/v1/users/me/favorites")).exchange().expectBody()
            .jsonPath("$.content.length()").isEqualTo(1).jsonPath("$.content[0].tacoId").isEqualTo("TACO1");
        as(bob[0], client.get().uri("/api/v1/users/me/favorites")).exchange().expectBody()
            .jsonPath("$.content.length()").isEqualTo(0);
        as(alice[0], client.put().uri("/api/v1/users/me/favorites/NOPE")).exchange().expectStatus().isNotFound();
        as(alice[0], client.delete().uri("/api/v1/users/me/favorites/TACO1")).exchange().expectStatus().isNoContent();
        as(alice[0], client.delete().uri("/api/v1/users/me/favorites/TACO1")).exchange().expectStatus().isNoContent();
        assertThat(count(Favorite.class)).isZero();
    }

    private void rate(String username, String tacoId, int score) {
        as(username, client.put().uri("/api/v1/tacos/" + tacoId + "/rating")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"score\":" + score + "}"))
            .exchange().expectStatus().isNoContent();
    }

    @Test
    void ratingsUpsertAndRankingRespectsMinimumVotesAndTieBreak() {
        String[] u1 = newCustomer();
        String[] u2 = newCustomer();
        String[] u3 = newCustomer();
        // TACO1: 5,4,3 -> 4.00 (3 votos). TACO2: 5,5,2 -> 4.00 (3 votos). TACO3: 5 -> excluido (1 voto).
        rate(u1[0], "TACO1", 1);
        rate(u1[0], "TACO1", 5); // repetir cambia el voto, no suma
        rate(u2[0], "TACO1", 4);
        rate(u3[0], "TACO1", 3);
        rate(u1[0], "TACO2", 5);
        rate(u2[0], "TACO2", 5);
        rate(u3[0], "TACO2", 2);
        rate(u1[0], "TACO3", 5);
        assertThat(count(TacoRating.class)).isEqualTo(7);

        client.get().uri("/api/v1/tacos/top?limit=10").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].tacoId").isEqualTo("TACO1")
            .jsonPath("$[0].average").isEqualTo(4.0)
            .jsonPath("$[0].votes").isEqualTo(3)
            .jsonPath("$[0].distribution.5").isEqualTo(1)
            .jsonPath("$[1].tacoId").isEqualTo("TACO2")
            .jsonPath("$[1].distribution.5").isEqualTo(2);

        as(u1[0], client.put().uri("/api/v1/tacos/TACO1/rating")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"score\":6}"))
            .exchange().expectStatus().isBadRequest();
        as(u1[0], client.put().uri("/api/v1/tacos/NOPE/rating")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"score\":3}"))
            .exchange().expectStatus().isNotFound();
        client.put().uri("/api/v1/tacos/TACO1/rating")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"score\":3}")
            .exchange().expectStatus().isUnauthorized();
    }
}
