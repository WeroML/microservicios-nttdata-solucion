package tacos.api.dto;

import java.time.Instant;

import lombok.Data;

// TC-33: vista pública; no expone al autor.
@Data
public class AnnouncementResponse {
    private String id;
    private String text;
    private String severity;
    private Instant createdAt;
    private Instant expiresAt;
}
