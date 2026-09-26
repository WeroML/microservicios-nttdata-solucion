package tacos.messaging.contract;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TC-27: contrato único de eventos de orden, versionado.
 *
 * Política de evolución:
 * - Campos nuevos siempre opcionales; un consumidor v1 ignora campos que no conoce.
 * - Nunca se cambia el significado de un campo existente; si es necesario,
 *   se crea un campo nuevo o se publica una nueva versión ("v2").
 * - eventId es un UUID global; correlationId y version siempre están presentes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent {

    public static final String CURRENT_VERSION = "v1";

    private String eventId = UUID.randomUUID().toString();
    private OrderEventType eventType;
    private String version = CURRENT_VERSION;
    private Instant occurredAt = Instant.now();
    private String correlationId;

    private OrderEventPayload payload;
}
