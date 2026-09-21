package tacos.web.api;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OutboxHealthIndicator implements ReactiveHealthIndicator {

    private final TacoMetricsService metricsService;

    public OutboxHealthIndicator(TacoMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public Mono<Health> health() {
        double backlog = metricsService.getOutboxBacklog();
        if (backlog > 1000) {
            return Mono.just(Health.down()
                    .withDetail("backlog", backlog)
                    .withDetail("message", "Outbox backlog is too high. Broker might be down.")
                    .build());
        }
        return Mono.just(Health.up()
                .withDetail("backlog", backlog)
                .build());
    }
}
