package tacos.api.dto;

import java.time.Instant;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Data;
import tacos.web.api.OpsAnnouncement.Severity;

@Data
public class AnnouncementRequest {
    // TC-33: texto acotado, sin caracteres de control (evita inyección en logs/UI).
    @NotBlank
    @Size(max = 280)
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain control characters")
    private String text;

    @NotNull
    private Severity severity;

    // Opcional; si falta se usa la vigencia por defecto. Nunca más allá del máximo configurado.
    private Instant expiresAt;
}
