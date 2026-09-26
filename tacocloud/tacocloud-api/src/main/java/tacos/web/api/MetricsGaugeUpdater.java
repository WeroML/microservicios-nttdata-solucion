package tacos.web.api;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import tacos.OrderStatus;
import tacos.OutboxEvent;
import tacos.TacoOrder;

// TC-32: el scheduler (borde de ejecución) suscribe y actualiza los gauges.
@Component
public class MetricsGaugeUpdater {

    private final ReactiveMongoTemplate mongo;
    private final TacoMetricsService metrics;

    public MetricsGaugeUpdater(ReactiveMongoTemplate mongo, TacoMetricsService metrics) {
        this.mongo = mongo;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${tacocloud.metrics.gauge-refresh:5000}")
    public void refresh() {
        refreshGauges().subscribe();
    }

    public Mono<Void> refreshGauges() {
        Mono<Long> backlog = mongo.count(Query.query(where("status")
            .in(OutboxEvent.OutboxStatus.NEW, OutboxEvent.OutboxStatus.PUBLISHING)), OutboxEvent.class);
        Mono<Long> failed = mongo.count(Query.query(where("status").is(OutboxEvent.OutboxStatus.FAILED)), OutboxEvent.class);
        Mono<Long> queue = mongo.count(Query.query(where("status").is(OrderStatus.CREATED)), TacoOrder.class);
        return Mono.zip(backlog, failed, queue)
            .doOnNext(t -> {
                metrics.updateOutbox(t.getT1(), t.getT2());
                metrics.updateKitchenQueue(t.getT3());
            })
            .then();
    }
}
