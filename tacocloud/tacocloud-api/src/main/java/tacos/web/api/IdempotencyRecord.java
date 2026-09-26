package tacos.web.api;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

// TC-34: la llave se delimita por usuario; el índice único resuelve la concurrencia.
@Data
@Document(collection="idempotency_records")
@CompoundIndex(name = "user_key_idx", def = "{'userId': 1, 'idempotencyKey': 1}", unique = true)
public class IdempotencyRecord {
    @Id
    private String id;
    private String userId;
    private String idempotencyKey;
    private String requestHash;
    private String orderId;
    private Status status;
    private Instant createdAt;
    private Instant updatedAt;

    // Retención: Mongo borra el registro cuando pasa esta fecha (índice TTL).
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public enum Status {
        IN_PROGRESS, COMPLETED, FAILED
    }
}
