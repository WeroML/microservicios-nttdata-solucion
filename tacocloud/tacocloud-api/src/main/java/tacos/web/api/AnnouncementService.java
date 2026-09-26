package tacos.web.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.api.dto.AnnouncementRequest;
import tacos.api.dto.AnnouncementResponse;
import tacos.api.error.BusinessRuleException;
import tacos.api.error.ConflictException;
import tacos.api.error.NotFoundException;

/**
 * TC-33: anuncios operativos persistentes y acotados.
 * - Texto validado (DTO), vigencia máxima configurable, tope de anuncios activos.
 * - Los expirados no se listan y Mongo los borra con el índice TTL.
 * - Cada anuncio es un documento con ID propio: escrituras concurrentes no
 *   comparten estado mutable. El tope de activos se revisa antes de insertar;
 *   dos altas simultáneas podrían excederlo por uno (riesgo aceptado y documentado).
 */
@Service
public class AnnouncementService {

    private final OpsAnnouncementRepository repo;
    private final AnnouncementProperties properties;
    private final Clock clock;

    public AnnouncementService(OpsAnnouncementRepository repo, AnnouncementProperties properties, Clock clock) {
        this.repo = repo;
        this.properties = properties;
        this.clock = clock;
    }

    public Flux<AnnouncementResponse> active() {
        return repo.findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(Instant.now(clock))
            .map(AnnouncementService::toResponse);
    }

    public Mono<AnnouncementResponse> create(AnnouncementRequest request, String author) {
        Instant now = Instant.now(clock);
        Instant expiresAt = request.getExpiresAt() != null ? request.getExpiresAt() : now.plus(properties.getDefaultTtl());
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(properties.getMaxTtl()))) {
            return Mono.error(new BusinessRuleException("INVALID_EXPIRATION",
                "expiresAt must be in the future and within " + properties.getMaxTtl().toDays() + " days."));
        }
        return repo.countByActiveTrueAndExpiresAtAfter(now)
            .flatMap(active -> {
                if (active >= properties.getMaxActive()) {
                    return Mono.error(new ConflictException("TOO_MANY_ANNOUNCEMENTS",
                        "There are already " + properties.getMaxActive() + " active announcements."));
                }
                OpsAnnouncement announcement = new OpsAnnouncement();
                announcement.setText(request.getText().trim());
                announcement.setSeverity(request.getSeverity());
                announcement.setCreatedAt(now);
                announcement.setExpiresAt(expiresAt);
                announcement.setCreatedBy(author);
                announcement.setActive(true);
                return repo.save(announcement);
            })
            .map(AnnouncementService::toResponse);
    }

    public Mono<Void> delete(String id) {
        return repo.findById(id)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("ANNOUNCEMENT_NOT_FOUND",
                "Announcement " + id + " not found.")))
            .flatMap(repo::delete);
    }

    static AnnouncementResponse toResponse(OpsAnnouncement announcement) {
        AnnouncementResponse resp = new AnnouncementResponse();
        resp.setId(announcement.getId());
        resp.setText(announcement.getText());
        resp.setSeverity(announcement.getSeverity().name());
        resp.setCreatedAt(announcement.getCreatedAt());
        resp.setExpiresAt(announcement.getExpiresAt());
        return resp;
    }
}
