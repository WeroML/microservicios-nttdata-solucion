package tacos.web.api;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface OpsAnnouncementRepository extends ReactiveCrudRepository<OpsAnnouncement, String> {
    Flux<OpsAnnouncement> findByActiveTrueAndExpiresAtAfter(Instant now);
}
