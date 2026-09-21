package tacos.web.api;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Endpoint(id="announcements", enableByDefault=true)
public class AnnouncementsEndpoint {

    private final OpsAnnouncementRepository repo;

    public AnnouncementsEndpoint(OpsAnnouncementRepository repo) {
        this.repo = repo;
    }

    @ReadOperation
    public Flux<OpsAnnouncement> getActiveAnnouncements() {
        return repo.findByActiveTrueAndExpiresAtAfter(Instant.now());
    }

    @WriteOperation
    public Mono<OpsAnnouncement> addAnnouncement(String text, String severity) {
        if (text == null || text.trim().isEmpty() || text.length() > 500) {
            return Mono.error(new IllegalArgumentException("Text must be between 1 and 500 characters"));
        }
        
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication().getName())
            .defaultIfEmpty("system")
            .flatMap(username -> {
                OpsAnnouncement ann = new OpsAnnouncement();
                ann.setText(text.trim());
                ann.setSeverity(severity != null ? severity : "INFO");
                ann.setCreatedAt(Instant.now());
                ann.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS)); // Limit expiration
                ann.setCreatedBy(username);
                ann.setActive(true);
                return repo.save(ann);
            });
    }

    @DeleteOperation
    public Mono<Void> deactivateAnnouncement(@Selector String id) {
        return repo.findById(id)
            .flatMap(ann -> {
                ann.setActive(false);
                return repo.save(ann);
            })
            .then();
    }
}
