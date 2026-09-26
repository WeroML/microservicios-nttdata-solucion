package tacos.web.api;

import java.time.Instant;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OpsAnnouncementRepository extends ReactiveCrudRepository<OpsAnnouncement, String> {
    Flux<OpsAnnouncement> findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);
    Mono<Long> countByActiveTrueAndExpiresAtAfter(Instant now);
}
