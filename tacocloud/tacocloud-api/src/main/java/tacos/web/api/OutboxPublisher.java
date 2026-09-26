package tacos.web.api;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tacos.OutboxEvent;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

/**
 * TC-29: publicador confiable del outbox.
 *
 * - Reclama eventos de uno en uno con findAndModify (NEW -> PUBLISHING); dos
 *   instancias nunca reclaman el mismo registro al mismo tiempo.
 * - Éxito: PUBLISHED. Fallo: vuelve a NEW con backoff exponencial; al llegar a
 *   maxAttempts queda FAILED y visible (métrica tacocloud.outbox.failed y health).
 * - Si el proceso muere con un evento en PUBLISHING, después de claimTimeout se
 *   vuelve a reclamar: la entrega es al menos una vez y la cocina deduplica (TC-30).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final ReactiveMongoTemplate mongo;
    private final OrderMessagingService messagingService;
    private final OutboxProperties properties;
    private final TacoMetricsService metrics;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxPublisher(ReactiveMongoTemplate mongo, OrderMessagingService messagingService,
                           OutboxProperties properties, TacoMetricsService metrics, Clock clock) {
        this.mongo = mongo;
        this.messagingService = messagingService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    // Borde de ejecución: el scheduler suscribe. La bandera evita corridas solapadas.
    @Scheduled(fixedDelayString = "${tacocloud.outbox.poll-interval:2000}")
    public void publishPending() {
        if (running.compareAndSet(false, true)) {
            publishBatch()
                .doFinally(signal -> running.set(false))
                .subscribe(count -> { }, error -> log.error("Outbox batch failed", error));
        }
    }

    // Procesa hasta batchSize eventos; termina en cuanto no quedan pendientes.
    public Mono<Long> publishBatch() {
        return Mono.defer(this::claimNext)
            .expand(claimed -> claimNext())
            .take(properties.getBatchSize(), true)
            .concatMap(this::publish)
            .count();
    }

    Mono<OutboxEvent> claimNext() {
        Instant now = Instant.now(clock);
        Criteria ready = new Criteria().orOperator(
            where("status").is(OutboxEvent.OutboxStatus.NEW).and("nextAttemptAt").lte(now),
            where("status").is(OutboxEvent.OutboxStatus.PUBLISHING)
                .and("claimedAt").lt(now.minus(properties.getClaimTimeout())));
        Query query = Query.query(ready).with(Sort.by("createdAt"));
        Update update = new Update()
            .set("status", OutboxEvent.OutboxStatus.PUBLISHING)
            .set("claimedBy", instanceId)
            .set("claimedAt", now)
            .set("updatedAt", now);
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), OutboxEvent.class);
    }

    private Mono<OutboxEvent> publish(OutboxEvent event) {
        OrderEvent payload = (OrderEvent) event.getPayload();
        return Mono.fromRunnable(() -> send(payload))
            .subscribeOn(Schedulers.boundedElastic())
            .then(markPublished(event))
            .onErrorResume(error -> markFailedAttempt(event, error));
    }

    private void send(OrderEvent payload) {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, payload.getCorrelationId());
        try {
            messagingService.sendOrderEvent(payload);
            log.info("Published event {} ({})", payload.getEventId(), payload.getEventType());
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);
        }
    }

    private Mono<OutboxEvent> markPublished(OutboxEvent event) {
        Instant now = Instant.now(clock);
        Update update = new Update()
            .set("status", OutboxEvent.OutboxStatus.PUBLISHED)
            .set("publishedAt", now)
            .set("updatedAt", now)
            .inc("attempts", 1);
        return updateClaimed(event, update)
            .doOnSuccess(e -> metrics.recordOutboxPublish("success"));
    }

    private Mono<OutboxEvent> markFailedAttempt(OutboxEvent event, Throwable error) {
        int attempts = event.getAttempts() + 1;
        boolean exhausted = attempts >= properties.getMaxAttempts();
        Instant now = Instant.now(clock);
        Update update = new Update()
            .set("attempts", attempts)
            // Sólo el tipo de error: el mensaje podría traer hosts o datos del broker.
            .set("lastError", error.getClass().getSimpleName())
            .set("updatedAt", now);
        if (exhausted) {
            update.set("status", OutboxEvent.OutboxStatus.FAILED);
        } else {
            update.set("status", OutboxEvent.OutboxStatus.NEW)
                .set("nextAttemptAt", now.plus(backoff(attempts)));
        }
        log.warn("Publishing event {} failed (attempt {}/{}): {}", event.getId(), attempts,
            properties.getMaxAttempts(), error.getClass().getSimpleName());
        metrics.recordOutboxPublish(exhausted ? "failed" : "retry");
        return updateClaimed(event, update);
    }

    // Backoff exponencial: initial * 2^(attempts-1), con tope maxBackoff.
    Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts - 1, 20);
        Duration delay = properties.getInitialBackoff().multipliedBy(factor);
        return delay.compareTo(properties.getMaxBackoff()) > 0 ? properties.getMaxBackoff() : delay;
    }

    // Sólo actualiza si este proceso sigue siendo el dueño del claim.
    private Mono<OutboxEvent> updateClaimed(OutboxEvent event, Update update) {
        Query query = Query.query(where("_id").is(event.getId()).and("claimedBy").is(instanceId)
            .and("status").is(OutboxEvent.OutboxStatus.PUBLISHING));
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), OutboxEvent.class);
    }
}
