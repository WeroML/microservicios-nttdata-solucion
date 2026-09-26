package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// TC-32: métricas de negocio con tags de baja cardinalidad y health degradado.
class TacoMetricsServiceTest {

    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList("source", "result"));

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TacoMetricsService metrics = new TacoMetricsService(registry);

    @Test
    void countersAndTimersChangeWithRealScenarios() {
        metrics.recordPlacement("api", "success", Duration.ofMillis(120));
        metrics.recordPlacement("api", "failed", Duration.ofMillis(30));
        metrics.recordCancelled("api");
        metrics.recordCouponApplied();
        metrics.recordStockRejected();
        metrics.recordKitchenClaimLatency(Duration.ofMinutes(3));

        assertThat(registry.counter("tacocloud.orders.placed", "source", "api", "result", "success").count()).isEqualTo(1);
        assertThat(registry.timer("tacocloud.orders.placement", "source", "api", "result", "success").count()).isEqualTo(1);
        assertThat(registry.counter("tacocloud.orders.cancelled", "source", "api").count()).isEqualTo(1);
        assertThat(registry.counter("tacocloud.coupons.applied").count()).isEqualTo(1);
        assertThat(registry.counter("tacocloud.inventory.rejections").count()).isEqualTo(1);
        assertThat(registry.timer("tacocloud.kitchen.claim.latency").totalTime(java.util.concurrent.TimeUnit.MINUTES))
            .isEqualTo(3.0);
    }

    @Test
    void onlyLowCardinalityTagsAreUsed() {
        metrics.recordPlacement("email", "success", Duration.ofMillis(5));
        metrics.recordOutboxPublish("retry");
        metrics.recordCancelled("KITCHEN");

        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                assertThat(ALLOWED_TAGS).contains(tag.getKey());
            }
        }
    }

    @Test
    void backlogGaugeReflectsTheLatestValue() {
        metrics.updateOutbox(7, 0);
        metrics.updateKitchenQueue(3);

        assertThat(registry.get("tacocloud.outbox.backlog").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("tacocloud.kitchen.queue").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void healthIsDegradedAndExplainsWhyWithoutCredentials() {
        OutboxHealthIndicator health = new OutboxHealthIndicator(metrics, 100);
        assertThat(health.health().block().getStatus().getCode()).isEqualTo("UP");

        metrics.updateOutbox(5, 2);
        Health degraded = health.health().block();
        assertThat(degraded.getStatus()).isEqualTo(OutboxHealthIndicator.DEGRADED);
        assertThat(degraded.getDetails()).containsEntry("failed", 2L).containsKey("reason");
        assertThat(degraded.getDetails().toString()).doesNotContain("password", "localhost", "amqp://");
    }
}
