package tacos.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

// TC-11 (y TC-32/TC-33): matriz de autorización con pruebas 200/401/403 por rol.
class SecurityMatrixIT extends IntegrationTestBase {

    @Test
    void anonymousCanReadCatalogButCannotOrder() {
        client.get().uri("/api/v1/ingredients").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/tacos").exchange().expectStatus().isOk();
        client.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType("application/problem+json")
            .expectBody().jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void userCreatesOrdersButCannotAdministerIngredients() {
        postOrder("habuma", orderJson(customerPaymentId("habuma"), 1, "FLTO", "GRBF"), null)
            .expectStatus().isCreated();
        as("habuma", client.delete().uri("/api/v1/ingredients/FLTO")).exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.code").isEqualTo("ACCESS_DENIED");
        as("habuma", client.post().uri("/api/v1/admin/ingredients/FLTO/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"amount\":5}"))
            .exchange().expectStatus().isForbidden();
    }

    @Test
    void adminAdministersIngredientsAndAuditsOrders() {
        as("admin", client.post().uri("/api/v1/admin/ingredients/FLTO/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"amount\":5}"))
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.stockOnHand").isEqualTo(105);
        as("admin", client.get().uri("/api/v1/admin/orders")).exchange().expectStatus().isOk();
        as("habuma", client.get().uri("/api/v1/admin/orders")).exchange().expectStatus().isForbidden();
    }

    @Test
    void kitchenWorksTheQueueButCannotAdministerOrOrder() {
        as("kitchen", client.get().uri("/api/v1/kitchen/queue")).exchange().expectStatus().isOk();
        as("habuma", client.get().uri("/api/v1/kitchen/queue")).exchange().expectStatus().isForbidden();
        as("kitchen", client.get().uri("/api/v1/admin/orders")).exchange().expectStatus().isForbidden();
        as("kitchen", client.get().uri("/data-api/users")).exchange().expectStatus().isForbidden();
        as("kitchen", client.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).bodyValue("{}"))
            .exchange().expectStatus().isForbidden();
    }

    @Test
    void unlistedRouteIsDeniedByDefault() {
        client.get().uri("/api/v1/secret-new-route").exchange().expectStatus().isUnauthorized();
        as("admin", client.get().uri("/api/v1/secret-new-route")).exchange().expectStatus().isForbidden();
        as("admin", client.get().uri("/not-a-ui-route")).exchange().expectStatus().isForbidden();
    }

    @Test
    void actuatorAndDataRestAreProtected() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.status").isEqualTo("UP").jsonPath("$.components").doesNotExist();
        as("admin", client.get().uri("/actuator/health")).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.components.mongo.status").isEqualTo("UP")
            .jsonPath("$.components.outbox").exists();
        client.get().uri("/actuator/metrics").exchange().expectStatus().isUnauthorized();
        as("habuma", client.get().uri("/actuator/metrics")).exchange().expectStatus().isForbidden();
        as("admin", client.get().uri("/actuator/metrics")).exchange().expectStatus().isOk();
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        as("habuma", client.get().uri("/data-api/users")).exchange().expectStatus().isForbidden();
    }

    @Test
    void announcementsReadForUsersWriteOnlyForAdmin() {
        String body = "{\"text\":\"Kitchen closes at 9pm\",\"severity\":\"INFO\"}";
        as("habuma", client.post().uri("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body))
            .exchange().expectStatus().isForbidden();
        as("admin", client.post().uri("/api/v1/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body))
            .exchange().expectStatus().isCreated();
        as("habuma", client.get().uri("/api/v1/announcements")).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$[0].text").isEqualTo("Kitchen closes at 9pm")
            .jsonPath("$[0].createdBy").doesNotExist();
        client.get().uri("/api/v1/announcements").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() {
        client.options().uri("/api/v1/tacos")
            .header("Origin", "http://localhost:4200").header("Access-Control-Request-Method", "GET")
            .exchange().expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
        client.options().uri("/api/v1/tacos")
            .header("Origin", "http://evil.example").header("Access-Control-Request-Method", "GET")
            .exchange().expectStatus().isForbidden();
    }
}
