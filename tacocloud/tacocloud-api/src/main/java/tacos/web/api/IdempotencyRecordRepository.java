package tacos.web.api;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface IdempotencyRecordRepository extends ReactiveCrudRepository<IdempotencyRecord, String> {
    Mono<IdempotencyRecord> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
