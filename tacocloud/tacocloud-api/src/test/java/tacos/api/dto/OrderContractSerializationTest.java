package tacos.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.TacoOrder;
import tacos.User;
import tacos.testsupport.Fixtures;
import tacos.web.api.OrderDraftFactory;

// TC-08: la API expone DTOs seguros y no acepta campos server-owned.
class OrderContractSerializationTest {

    // Mismo ObjectMapper que configura Spring Boot (ignora campos desconocidos).
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final OrderMapper orderMapper = new OrderMapper(new TacoMapper(new IngredientMapper()));

    private TacoOrder order() {
        Map<String, Ingredient> catalog = Fixtures.catalog();
        TacoOrder order = Fixtures.draftFactory(catalog).build(Fixtures.USER_ID, Fixtures.simpleOrderRequest()).block();
        order.setId("order-1");
        return order;
    }

    @Test
    void orderJsonHasNoSensitiveOrInternalFields() throws Exception {
        String json = mapper.writeValueAsString(orderMapper.toResponse(order()));

        assertThat(json).doesNotContain("password", "ccNumber", "ccCVV", "ccExpiration", "authorities",
            "paymentToken", "tok_", "userId", "\"user\"", "version", "stockOnHand");
        assertThat(json).contains("\"last4\":\"1111\"", "\"brand\":\"VISA\"");
    }

    @Test
    void userEntityIsNeverPartOfTheOrderContract() {
        assertThat(OrderResponse.class.getDeclaredFields()).extracting("type").doesNotContain(User.class);
    }

    @Test
    void mapsEntityToResponse() {
        TacoOrder order = order();
        OrderResponse response = orderMapper.toResponse(order);

        assertThat(response.getId()).isEqualTo("order-1");
        assertThat(response.getTotal()).isEqualByComparingTo(order.getTotal());
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getTaco().getIngredients()).extracting("id")
            .containsExactly("FLTO", "GRBF", "CHED");
    }

    // Mass assignment: id, placedAt, status, total y userId del cliente no llegan al dominio.
    @Test
    void extraFieldsInTheRequestDoNotAlterTheDomain() throws Exception {
        String json = "{\"id\":\"hacked\",\"userId\":\"" + Fixtures.OTHER_USER_ID + "\",\"status\":\"DELIVERED\","
            + "\"placedAt\":\"2000-01-01T00:00:00Z\",\"total\":0.01,\"subtotal\":0.01,"
            + "\"deliveryName\":\"Craig\",\"deliveryStreet\":\"1 St\",\"deliveryCity\":\"X\","
            + "\"deliveryState\":\"TX\",\"deliveryZip\":\"76227\",\"paymentMethodId\":\"" + Fixtures.PAYMENT_ID + "\","
            + "\"items\":[{\"quantity\":1,\"unitPriceAtPurchase\":0.01,"
            + "\"taco\":{\"name\":\"Classic\",\"ingredientIds\":[\"FLTO\",\"GRBF\"],\"dietaryTags\":[\"VEGAN\"]}}]}";
        OrderCreateRequest request = mapper.readValue(json, OrderCreateRequest.class);
        OrderDraftFactory factory = Fixtures.draftFactory(Fixtures.catalog());

        StepVerifier.create(factory.build(Fixtures.USER_ID, request))
            .assertNext(order -> {
                assertThat(order.getId()).isNull();
                assertThat(order.getUserId()).isEqualTo(Fixtures.USER_ID);
                assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("1.75"));
                assertThat(order.getItems().get(0).getUnitPriceAtPurchase()).isEqualByComparingTo("1.75");
                assertThat(order.getItems().get(0).getTaco().getDietaryTags()).doesNotContain(Ingredient.DietaryTag.VEGAN);
                assertThat(order.getPlacedAt().toInstant()).isEqualTo(Fixtures.CLOCK.instant());
            })
            .verifyComplete();
    }

    @Test
    void requestDtoHasNoServerOwnedFields() {
        assertThat(OrderCreateRequest.class.getDeclaredFields()).extracting("name")
            .doesNotContain("id", "userId", "placedAt", "status", "total", "subtotal");
    }
}
