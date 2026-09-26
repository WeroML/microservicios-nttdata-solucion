package tacos.it;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import tacos.Favorite;
import tacos.Ingredient;
import tacos.OutboxEvent;
import tacos.PaymentMethod;
import tacos.StockReservation;
import tacos.TacoOrder;
import tacos.TacoRating;
import tacos.User;
import tacos.data.PaymentMethodRepository;
import tacos.data.UserRepository;
import tacos.web.api.IdempotencyRecord;
import tacos.web.api.OpsAnnouncement;

/**
 * TC-36: base de la suite de integración. Levanta la aplicación real (Spring MVC,
 * seguridad, Mongo embebido como replica set) en un puerto aleatorio y limpia el
 * estado que cada prueba modifica. El publicador del outbox no corre solo durante
 * las pruebas (poll-interval largo): cada prueba lo invoca cuando lo necesita.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "tacocloud.outbox.poll-interval=3600000",
    "tacocloud.metrics.gauge-refresh=3600000",
    "spring.boot.admin.client.enabled=false"
})
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "password";
    public static final List<String> SEED_INGREDIENTS = Arrays.asList(
        "FLTO", "COTO", "GRBF", "CARN", "TMTO", "LETC", "CHED", "JACK", "SLSA", "SRCR");

    @LocalServerPort
    protected int port;

    @Autowired
    protected ReactiveMongoTemplate mongo;

    @Autowired
    protected UserRepository userRepo;

    @Autowired
    protected PaymentMethodRepository paymentMethodRepo;

    @Autowired
    protected PasswordEncoder encoder;

    protected WebTestClient client;

    @BeforeEach
    void resetState() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(30)).build();
        Mono.when(
                mongo.remove(new Query(), TacoOrder.class),
                mongo.remove(new Query(), OutboxEvent.class),
                mongo.remove(new Query(), StockReservation.class),
                mongo.remove(new Query(), IdempotencyRecord.class),
                mongo.remove(new Query(), Favorite.class),
                mongo.remove(new Query(), TacoRating.class),
                mongo.remove(new Query(), OpsAnnouncement.class),
                mongo.remove(Query.query(where("_id").nin(SEED_INGREDIENTS)), Ingredient.class),
                mongo.updateMulti(Query.query(where("_id").in(SEED_INGREDIENTS)),
                    new Update().set("stockOnHand", 100).set("available", true), Ingredient.class))
            .block();
    }

    protected WebTestClient.RequestHeadersSpec<?> as(String username, WebTestClient.RequestHeadersSpec<?> spec) {
        return spec.headers(h -> h.setBasicAuth(username, PASSWORD));
    }

    /** Crea un usuario sintético con un método de pago tokenizado; devuelve [username, paymentMethodId]. */
    protected String[] newCustomer() {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(username, encoder.encode(PASSWORD), "Test Customer", "1 Test St", "Testville",
            "TX", "12345", "555", username + "@tacocloud.test");
        user.setRoles(new HashSet<>(Collections.singleton(User.ROLE_USER)));
        User saved = userRepo.save(user).block();
        PaymentMethod pm = paymentMethodRepo.save(
            new PaymentMethod(saved.getId(), "tok_it_" + username, "VISA", "4242", "12/30")).block();
        return new String[] {username, pm.getId()};
    }

    protected String customerPaymentId(String username) {
        User user = userRepo.findByUsername(username).block();
        return paymentMethodRepo.findByUserId(user.getId()).blockFirst().getId();
    }

    protected static String orderJson(String paymentMethodId, int quantity, String... ingredientIds) {
        StringBuilder ids = new StringBuilder();
        for (String id : ingredientIds) {
            ids.append(ids.length() == 0 ? "" : ",").append('"').append(id).append('"');
        }
        return "{\"deliveryName\":\"Test Customer\",\"deliveryStreet\":\"1 Test St\",\"deliveryCity\":\"Testville\","
            + "\"deliveryState\":\"TX\",\"deliveryZip\":\"12345\",\"paymentMethodId\":\"" + paymentMethodId + "\","
            + "\"items\":[{\"taco\":{\"name\":\"Integration taco\",\"ingredientIds\":[" + ids + "]},"
            + "\"quantity\":" + quantity + "}]}";
    }

    protected WebTestClient.ResponseSpec postOrder(String username, String json, String idempotencyKey) {
        WebTestClient.RequestBodySpec spec = client.post().uri("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBasicAuth(username, PASSWORD));
        if (idempotencyKey != null) {
            spec = spec.header("Idempotency-Key", idempotencyKey);
        }
        return spec.bodyValue(json).exchange();
    }

    protected int stockOf(String ingredientId) {
        return mongo.findById(ingredientId, Ingredient.class).block().getStockOnHand();
    }

    protected long count(Class<?> type) {
        return mongo.count(new Query(), type).block();
    }
}
