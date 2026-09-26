package tacos.web.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * TC-32: salud del outbox (y, por extensión, del broker). Si hay eventos
 * agotados o el backlog pasa el umbral, el componente queda DEGRADED y explica
 * por qué; nunca incluye hosts ni credenciales.
 */
@Component("outbox")
public class OutboxHealthIndicator implements ReactiveHealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED");

    private final TacoMetricsService metrics;
    private final long backlogThreshold;

    public OutboxHealthIndicator(TacoMetricsService metrics,
                                 @Value("${tacocloud.outbox.backlog-threshold:100}") long backlogThreshold) {
        this.metrics = metrics;
        this.backlogThreshold = backlogThreshold;
    }

    @Override
    public Mono<Health> health() {
        long backlog = metrics.getOutboxBacklog();
        long failed = metrics.getOutboxFailed();
        Health.Builder builder = (failed > 0 || backlog > backlogThreshold)
            ? Health.status(DEGRADED).withDetail("reason",
                failed > 0 ? "Some events exhausted their retries" : "Outbox backlog above threshold")
            : Health.up();
        return Mono.just(builder
            .withDetail("backlog", backlog)
            .withDetail("failed", failed)
            .withDetail("backlogThreshold", backlogThreshold)
            .build());
    }
}
