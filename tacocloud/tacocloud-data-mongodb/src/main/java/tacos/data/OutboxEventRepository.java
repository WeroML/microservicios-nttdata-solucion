package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import tacos.OutboxEvent;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, String> {
    Flux<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);
}
