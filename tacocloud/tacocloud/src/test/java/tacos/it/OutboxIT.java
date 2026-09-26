package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;
import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;
import tacos.web.api.LegacyPaymentDataMigration;
import tacos.web.api.MetricsGaugeUpdater;
import tacos.web.api.OutboxProperties;
import tacos.web.api.OutboxPublisher;
import tacos.web.api.TacoMetricsService;

// TC-29: outbox confiable (entrega al menos una vez). TC-12: migración. TC-32: métricas reales.
class OutboxIT extends IntegrationTestBase {

    @Autowired
    private TacoMetricsService metrics;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private MetricsGaugeUpdater gaugeUpdater;

    @Autowired
    private LegacyPaymentDataMigration migration;

    /** Broker simulado que puede caerse y recuperarse. */
    static class FlakyBroker implements OrderMessagingService {
        final AtomicBoolean down = new AtomicBoolean(false);
        final List<String> delivered = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void sendOrderEvent(OrderEvent event) {
            if (down.get()) {
                throw new IllegalStateException("broker unavailable");
            }
            delivered.add(event.getEventId());
        }
    }

    private OutboxPublisher publisher(OrderMessagingService broker, int maxAttempts, Clock clock) {
        OutboxProperties props = new OutboxProperties();
        props.setMaxAttempts(maxAttempts);
        props.setInitialBackoff(Duration.ZERO);
        props.setMaxBackoff(Duration.ZERO);
        props.setClaimTimeout(Duration.ofMinutes(1));
        return new OutboxPublisher(mongo, broker, props, metrics, clock);
    }

    private void placeOrder() {
        String[] customer = newCustomer();
        postOrder(customer[0], orderJson(customer[1], 1, "FLTO", "GRBF"), null).expectStatus().isCreated();
    }

    private List<OutboxEvent> outbox() {
        return mongo.findAll(OutboxEvent.class).collectList().block();
    }

    @Test
    void confirmedOrderAlwaysHasItsOutboxEvent() {
        placeOrder();

        assertThat(count(TacoOrder.class)).isEqualTo(1);
        assertThat(outbox()).hasSize(1).allMatch(e -> e.getStatus() == OutboxEvent.OutboxStatus.NEW);
    }

    @Test
    void brokerFailureKeepsTheEventRetryableAndRecoveryPublishesIt() {
        placeOrder();
        FlakyBroker broker = new FlakyBroker();
        broker.down.set(true);
        OutboxPublisher publisher = publisher(broker, 5, Clock.systemUTC());

        publisher.publishBatch().block();
        OutboxEvent afterFailure = outbox().get(0);
        assertThat(afterFailure.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.NEW);
        assertThat(afterFailure.getAttempts()).isEqualTo(1);
        assertThat(afterFailure.getLastError()).isEqualTo("IllegalStateException");

        broker.down.set(false);
        publisher.publishBatch().block();
        assertThat(outbox().get(0).getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
        assertThat(broker.delivered).hasSize(1);
    }

    @Test
    void exhaustedEventsStayVisibleAsFailed() {
        placeOrder();
        FlakyBroker broker = new FlakyBroker();
        broker.down.set(true);
        OutboxPublisher publisher = publisher(broker, 2, Clock.systemUTC());

        publisher.publishBatch().block();
        publisher.publishBatch().block();
        publisher.publishBatch().block();

        assertThat(outbox().get(0).getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
        gaugeUpdater.refreshGauges().block();
        assertThat(registry.get("tacocloud.outbox.failed").gauge().value()).isEqualTo(1.0);
        as("admin", client.get().uri("/actuator/health")).exchange().expectBody()
            .jsonPath("$.components.outbox.status").isEqualTo("DEGRADED")
            .jsonPath("$.components.outbox.details.reason").exists();
    }

    @Test
    void twoPublishersNeverDeliverTheSameClaimTwice() {
        for (int i = 0; i < 5; i++) {
            placeOrder();
        }
        FlakyBroker broker = new FlakyBroker();
        OutboxPublisher a = publisher(broker, 5, Clock.systemUTC());
        OutboxPublisher b = publisher(broker, 5, Clock.systemUTC());

        Mono.when(a.publishBatch(), b.publishBatch()).block();

        assertThat(broker.delivered).hasSize(5).doesNotHaveDuplicates();
        assertThat(outbox()).allMatch(e -> e.getStatus() == OutboxEvent.OutboxStatus.PUBLISHED);
    }

    @Test
    void restartResumesEventsLeftInPublishing() {
        placeOrder();
        // Simula un proceso que murió después de reclamar el evento.
        mongo.updateMulti(new Query(), new Update().set("status", OutboxEvent.OutboxStatus.PUBLISHING)
            .set("claimedBy", "dead-instance").set("claimedAt", Instant.now().minus(Duration.ofMinutes(5))),
            OutboxEvent.class).block();
        FlakyBroker broker = new FlakyBroker();

        publisher(broker, 5, Clock.systemUTC()).publishBatch().block();

        assertThat(broker.delivered).hasSize(1);
        assertThat(outbox().get(0).getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
    }

    @Test
    void recentClaimOfAnotherInstanceIsNotStolen() {
        placeOrder();
        mongo.updateMulti(new Query(), new Update().set("status", OutboxEvent.OutboxStatus.PUBLISHING)
            .set("claimedBy", "other-live-instance").set("claimedAt", Instant.now()), OutboxEvent.class).block();
        FlakyBroker broker = new FlakyBroker();

        publisher(broker, 5, Clock.fixed(Instant.now(), ZoneOffset.UTC)).publishBatch().block();

        assertThat(broker.delivered).isEmpty();
    }

    // ---- TC-12 ----

    @Test
    void migrationRemovesLegacyCardFields() {
        String orders = mongo.getCollectionName(TacoOrder.class);
        mongo.insert(new Document("_id", "legacy-1").append("deliveryName", "Old").append("ccNumber", "4111111111111111")
            .append("ccCVV", "123").append("ccExpiration", "10/25"), orders).block();

        Long cleaned = migration.removeLegacyCardData().block();

        Document after = mongo.findById("legacy-1", Document.class, orders).block();
        assertThat(cleaned).isGreaterThanOrEqualTo(1L);
        assertThat(after).containsKey("deliveryName").doesNotContainKeys("ccNumber", "ccCVV", "ccExpiration");
        assertThat(mongo.count(Query.query(where("ccCVV").exists(true)), orders).block()).isZero();
    }

    // ---- TC-32 ----

    @Test
    void businessMetricsChangeWithRealScenarios() {
        double before = placedCounter();
        placeOrder();

        assertThat(placedCounter()).isEqualTo(before + 1);
        as("admin", client.get().uri("/actuator/metrics/tacocloud.orders.placed")).exchange()
            .expectStatus().isOk().expectBody()
            .jsonPath("$.availableTags[?(@.tag == 'source')]").exists();
        gaugeUpdater.refreshGauges().block();
        assertThat(registry.get("tacocloud.outbox.backlog").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("tacocloud.kitchen.queue").gauge().value()).isEqualTo(1.0);
    }

    private double placedCounter() {
        io.micrometer.core.instrument.Counter counter = registry.find("tacocloud.orders.placed")
            .tags("source", "api", "result", "success").counter();
        return counter == null ? 0 : counter.count();
    }
}
