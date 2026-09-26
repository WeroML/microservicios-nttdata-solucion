package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.web.api.OrderService;

/**
 * TC-29/TC-36: la misma aplicación contra MongoDB real en contenedor (replica set,
 * requisito de las transacciones). Se omite automáticamente si no hay Docker; en CI
 * sí corre y el pipeline falla si se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "tacocloud.outbox.poll-interval=3600000",
    "spring.boot.admin.client.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration"
})
class MongoReplicaSetContainerIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:4.4");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.mongodb.embedded.storage.repl-set-name", () -> "");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private ReactiveMongoTemplate mongo;

    @Autowired
    private tacos.data.UserRepository userRepo;

    @Test
    void orderAndOutboxAreCommittedTogetherOnARealReplicaSet() {
        tacos.User habuma = userRepo.findByUsername("habuma").block();
        tacos.api.dto.OrderCreateRequest request = new tacos.api.dto.OrderCreateRequest();
        request.setDeliveryName("Craig");
        request.setDeliveryStreet("1 St");
        request.setDeliveryCity("X");
        request.setDeliveryState("TX");
        request.setDeliveryZip("76227");
        request.setPaymentMethodId(mongo.findOne(Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("userId").is(habuma.getId())),
            tacos.PaymentMethod.class).block().getId());
        tacos.api.dto.TacoRequest taco = new tacos.api.dto.TacoRequest();
        taco.setName("Container taco");
        taco.setIngredientIds(java.util.Arrays.asList("FLTO", "GRBF"));
        tacos.api.dto.OrderCreateRequest.OrderItemRequest item = new tacos.api.dto.OrderCreateRequest.OrderItemRequest();
        item.setTaco(taco);
        item.setQuantity(1);
        request.setItems(java.util.Collections.singletonList(item));

        orderService.placeOrder(request, tacos.web.api.Actor.from(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                habuma, null, habuma.getAuthorities())), null).block();

        assertThat(mongo.count(new Query(), TacoOrder.class).block()).isEqualTo(1);
        assertThat(mongo.count(new Query(), OutboxEvent.class).block()).isEqualTo(1);
    }
}
