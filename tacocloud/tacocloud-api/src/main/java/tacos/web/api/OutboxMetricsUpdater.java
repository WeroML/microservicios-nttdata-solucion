package tacos.web.api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import tacos.OutboxEvent;

@Component
public class OutboxMetricsUpdater {

    private final ReactiveMongoTemplate mongoTemplate;
    private final TacoMetricsService metricsService;

    public OutboxMetricsUpdater(ReactiveMongoTemplate mongoTemplate, TacoMetricsService metricsService) {
        this.mongoTemplate = mongoTemplate;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelay = 5000)
    public void updateMetrics() {
        Query query = new Query(Criteria.where("status").is(OutboxEvent.OutboxStatus.NEW));
        mongoTemplate.count(query, OutboxEvent.class)
            .subscribe(count -> metricsService.updateBacklog(count.intValue()));
    }
}
