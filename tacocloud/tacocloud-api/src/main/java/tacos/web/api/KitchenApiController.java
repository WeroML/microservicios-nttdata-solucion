package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.OrderStatus;
import tacos.TacoOrder;
import tacos.User;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import tacos.OrderStatusHistory;
import java.time.Instant;

@RestController
@RequestMapping(path="/api/v1/kitchen", produces="application/json")
@PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
public class KitchenApiController {

    private final ReactiveMongoTemplate mongoTemplate;
    private final OrderStateService stateService;

    public KitchenApiController(ReactiveMongoTemplate mongoTemplate, OrderStateService stateService) {
        this.mongoTemplate = mongoTemplate;
        this.stateService = stateService;
    }

    @GetMapping("/queue")
    public Flux<TacoOrder> getQueue() {
        Query query = new Query(Criteria.where("status").is(OrderStatus.CREATED))
                .with(Sort.by(Sort.Direction.ASC, "placedAt", "id"));
        
        return mongoTemplate.find(query, TacoOrder.class);
    }

    @PostMapping("/orders/claim")
    public Mono<ResponseEntity<TacoOrder>> claimNextOrder(@AuthenticationPrincipal User user) {
        String stationId = "STATION-1"; // Or dynamic based on user profile
        String cookId = user.getUsername();

        Query query = new Query(Criteria.where("status").is(OrderStatus.CREATED))
                .with(Sort.by(Sort.Direction.ASC, "placedAt", "id"));
        
        Update update = new Update()
                .set("status", OrderStatus.ACCEPTED)
                .set("stationId", stationId)
                .set("cookId", cookId)
                .set("estimatedPrepMinutes", calculateETA()) // Dynamic formula
                .push("statusHistory", new OrderStatusHistory(OrderStatus.ACCEPTED, Instant.now(), cookId, "Claimed by kitchen"));

        // Atomic claim
        return mongoTemplate.findAndModify(query, update, org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), TacoOrder.class)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build()); // Queue empty
    }

    private int calculateETA() {
        // ETA based on queue size and complexity
        // For simplicity, returning a fixed value + random for now, or just a fixed value.
        return 15;
    }
}
