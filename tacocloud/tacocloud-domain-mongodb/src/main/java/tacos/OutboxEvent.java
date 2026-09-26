package tacos;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

// TC-29: registro outbox guardado en la misma transacción que la orden.
@Data
@NoArgsConstructor
@Document(collection = "outbox_events")
@CompoundIndex(name = "status_next_attempt_idx", def = "{'status': 1, 'nextAttemptAt': 1}")
public class OutboxEvent {

    @Id
    private String id; // mismo valor que eventId del contrato
    private String aggregateId;
    private String aggregateType;
    private String type;
    private String eventVersion;
    private Object payload; // OrderEvent del contrato (TC-27)

    private OutboxStatus status = OutboxStatus.NEW;
    private int attempts;
    private Instant nextAttemptAt;
    private String claimedBy;
    private Instant claimedAt;
    private String lastError;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    public enum OutboxStatus {
        NEW, PUBLISHING, PUBLISHED, FAILED
    }
}
