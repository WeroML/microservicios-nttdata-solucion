package tacos.web.api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.slf4j.MDC;
import tacos.OutboxEvent;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@Slf4j
public class OutboxPublisher {

    private final ReactiveMongoTemplate mongoTemplate;
    private final OrderMessagingService messagingService;

    public OutboxPublisher(ReactiveMongoTemplate mongoTemplate, OrderMessagingService messagingService) {
        this.mongoTemplate = mongoTemplate;
        this.messagingService = messagingService;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        Query query = new Query(Criteria.where("status").is(OutboxEvent.OutboxStatus.NEW));
        Update update = new Update()
                .set("status", OutboxEvent.OutboxStatus.PUBLISHING)
                .set("updatedAt", Instant.now());

        // Atomically claim one event
        mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), OutboxEvent.class)
            .flatMap(event -> {
                OrderEvent payload = (OrderEvent) event.getPayload();
                if (payload != null && payload.getCorrelationId() != null) {
                    MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, payload.getCorrelationId());
                }
                
                try {
                    messagingService.sendOrderEvent(payload);
                    event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                } catch (Exception e) {
                    log.error("Failed to publish event {}", event.getId(), e);
                    event.setRetries(event.getRetries() + 1);
                    if (event.getRetries() > 3) {
                        event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    } else {
                        event.setStatus(OutboxEvent.OutboxStatus.NEW); // Retry
                    }
                } finally {
                    MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);
                }
                event.setUpdatedAt(Instant.now());
                return mongoTemplate.save(event);
            })
            .repeat() // Continue claiming until no more events
            .onErrorResume(e -> {
                log.error("Error in OutboxPublisher", e);
                return Mono.empty();
            })
            .subscribe();
    }
}
