package tacos.web.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import tacos.OutboxEvent;
import reactor.core.publisher.Mono;
import javax.annotation.PostConstruct;

@Service
public class TacoMetricsService {

    private final MeterRegistry meterRegistry;
    private final ReactiveMongoTemplate mongoTemplate;

    public TacoMetricsService(MeterRegistry meterRegistry, ReactiveMongoTemplate mongoTemplate) {
        this.meterRegistry = meterRegistry;
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void registerGauges() {
        // "Gauges deben leer de forma segura sin block() en InfoContributor." -> 
        // Micrometer handles Gauge sampling. We must not block.
        // Wait, Micrometer Gauges execute synchronously when scraped. If we do mongoTemplate.count().block(), it violates the rule.
        // Instead, we can schedule an async updater that updates an AtomicInteger, and the Gauge reads the AtomicInteger.
        
        meterRegistry.gauge("tacocloud.outbox.backlog", this, TacoMetricsService::getOutboxBacklog);
    }
    
    private int cachedBacklog = 0;

    // A scheduled task will update this
    public void updateBacklog(int backlog) {
        this.cachedBacklog = backlog;
    }

    public double getOutboxBacklog() {
        return cachedBacklog;
    }

    public void recordOrderCreated(String status) {
        meterRegistry.counter("tacocloud.orders", "status", status, "result", "success").increment();
    }

    public void recordOrderFailed(String status) {
        meterRegistry.counter("tacocloud.orders", "status", status, "result", "failed").increment();
    }

    public void recordStockRejected() {
        meterRegistry.counter("tacocloud.inventory.rejects").increment();
    }

    public void recordCouponApplied() {
        meterRegistry.counter("tacocloud.coupons.applied").increment();
    }
}
