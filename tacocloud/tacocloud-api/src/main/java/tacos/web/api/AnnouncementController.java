package tacos.web.api;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.api.dto.AnnouncementRequest;
import tacos.api.dto.AnnouncementResponse;

/**
 * TC-33: los anuncios son funcionalidad de negocio, no de operación, por eso
 * salen de Actuator a una API REST.
 * Política: lectura para usuarios autenticados; alta y borrado sólo ADMIN.
 */
@RestController
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping(path={"/api/v1/announcements", "/api/announcements"}, produces="application/json")
    public Flux<AnnouncementResponse> activeAnnouncements() {
        return announcementService.active();
    }

    @PostMapping(path={"/api/v1/admin/announcements", "/api/admin/announcements"},
        consumes="application/json", produces="application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<AnnouncementResponse> createAnnouncement(@Valid @RequestBody AnnouncementRequest request,
                                                         Authentication authentication) {
        return announcementService.create(request, authentication.getName());
    }

    @DeleteMapping(path={"/api/v1/admin/announcements/{id}", "/api/admin/announcements/{id}"})
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteAnnouncement(@PathVariable("id") String id) {
        return announcementService.delete(id)
            .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
