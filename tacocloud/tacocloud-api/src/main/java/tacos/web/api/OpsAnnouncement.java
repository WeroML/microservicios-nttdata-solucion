package tacos.web.api;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

// TC-33: anuncio persistente con ID estable (reemplaza el índice de lista de NotesEndpoint).
@Data
@Document(collection="ops_announcements")
public class OpsAnnouncement {
    @Id
    private String id;
    private String text;
    private Severity severity;
    private Instant createdAt;
    private String createdBy;
    private boolean active;

    // Índice TTL: Mongo limpia los anuncios expirados automáticamente.
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public enum Severity {
        INFO, WARNING, CRITICAL
    }
}
