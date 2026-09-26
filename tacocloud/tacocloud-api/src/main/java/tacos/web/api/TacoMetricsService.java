package tacos.web.api;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * TC-32: métricas de negocio. Sólo tags de baja cardinalidad
 * (source, result, reason); nunca orderId, userId ni correlationId.
 *
 * Nombre                               Tipo     Tags            Unidad
 * tacocloud.orders.placed              counter  source, result  órdenes
 * tacocloud.orders.placement           timer    source, result  segundos
 * tacocloud.orders.cancelled           counter  source          órdenes
 * tacocloud.coupons.applied            counter  -               cupones
 * tacocloud.inventory.rejections       counter  -               órdenes rechazadas por stock
 * tacocloud.kitchen.claim.latency      timer    -               segundos desde placedAt hasta el claim
 * tacocloud.outbox.published           counter  result          eventos (success, retry, failed)
 * tacocloud.outbox.backlog             gauge    -               eventos NEW + PUBLISHING
 * tacocloud.outbox.failed              gauge    -               eventos FAILED (equivalente a DLQ del productor)
 * tacocloud.kitchen.queue              gauge    -               órdenes CREATED esperando cocina
 *
 * Los gauges leen valores en memoria que actualiza MetricsGaugeUpdater; nunca hacen block().
 */
@Service
public class TacoMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong outboxFailed = new AtomicLong();
    private final AtomicLong kitchenQueue = new AtomicLong();

    public TacoMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("tacocloud.outbox.backlog", outboxBacklog);
        meterRegistry.gauge("tacocloud.outbox.failed", outboxFailed);
        meterRegistry.gauge("tacocloud.kitchen.queue", kitchenQueue);
    }

    public void recordPlacement(String source, String result, Duration elapsed) {
        meterRegistry.counter("tacocloud.orders.placed", "source", source, "result", result).increment();
        Timer.builder("tacocloud.orders.placement")
            .tags("source", source, "result", result)
            .register(meterRegistry)
            .record(elapsed);
    }

    public void recordCancelled(String source) {
        meterRegistry.counter("tacocloud.orders.cancelled", "source", source).increment();
    }

    public void recordCouponApplied() {
        meterRegistry.counter("tacocloud.coupons.applied").increment();
    }

    public void recordStockRejected() {
        meterRegistry.counter("tacocloud.inventory.rejections").increment();
    }

    public void recordKitchenClaimLatency(Duration waited) {
        Timer.builder("tacocloud.kitchen.claim.latency").register(meterRegistry).record(waited);
    }

    public void recordOutboxPublish(String result) {
        meterRegistry.counter("tacocloud.outbox.published", "result", result).increment();
    }

    public void updateOutbox(long backlog, long failed) {
        outboxBacklog.set(backlog);
        outboxFailed.set(failed);
    }

    public void updateKitchenQueue(long queued) {
        kitchenQueue.set(queued);
    }

    public long getOutboxBacklog() {
        return outboxBacklog.get();
    }

    public long getOutboxFailed() {
        return outboxFailed.get();
    }
}
