package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tacos.Ingredient;
import tacos.OrderItem;
import tacos.OrderStatus;
import tacos.TacoOrder;
import tacos.testsupport.Fixtures;

// TC-26: ETA determinista y DTO de cocina sin datos sensibles.
class KitchenServiceTest {

    private final Map<String, Ingredient> catalog = Fixtures.catalog();
    private final KitchenService service = new KitchenService(mock(ReactiveMongoTemplate.class),
        mock(TransactionalOrderService.class), new KitchenProperties(),
        new TacoMetricsService(new SimpleMeterRegistry()), Fixtures.CLOCK);

    private TacoOrder order(int quantity, String... ingredients) {
        TacoOrder order = new TacoOrder();
        order.setId("o1");
        order.setStatus(OrderStatus.CREATED);
        order.setUserId(Fixtures.USER_ID);
        order.setDeliveryStreet("123 North Street");
        order.setPaymentLast4("1111");
        order.setTotal(new BigDecimal("9.99"));
        OrderItem item = new OrderItem();
        item.setTaco(Fixtures.taco(null, "Classic", catalog, ingredients));
        item.setQuantity(quantity);
        order.addItem(item);
        return order;
    }

    @Test
    void etaFollowsTheConfiguredFormula() {
        // base 5 + 2 x 2 unidades + 1 x 3 ingredientes + 3 x 1 orden por delante = 15
        assertThat(service.estimate(order(2, "FLTO", "GRBF", "CHED"), 1)).isEqualTo(15);
    }

    @Test
    void etaGrowsWithQueueQuantityAndComplexityAndIsDeterministic() {
        TacoOrder small = order(1, "COTO", "TMTO");
        assertThat(service.estimate(small, 0)).isEqualTo(service.estimate(small, 0));
        assertThat(service.estimate(small, 3)).isGreaterThan(service.estimate(small, 0));
        assertThat(service.estimate(order(4, "COTO", "TMTO"), 0)).isGreaterThan(service.estimate(small, 0));
        assertThat(service.estimate(order(1, "COTO", "TMTO", "GRBF", "CHED"), 0))
            .isGreaterThan(service.estimate(small, 0));
    }

    @Test
    void kitchenResponseHasNoPaymentUserOrFullAddress() throws Exception {
        String json = Jackson2ObjectMapperBuilder.json().build()
            .writeValueAsString(KitchenService.toResponse(order(1, "FLTO", "GRBF"), 9));

        assertThat(json).doesNotContain("userId", "123 North Street", "1111", "payment", "total", "password");
        assertThat(json).contains("\"estimatedPrepMinutes\":9", "Classic");
    }
}
