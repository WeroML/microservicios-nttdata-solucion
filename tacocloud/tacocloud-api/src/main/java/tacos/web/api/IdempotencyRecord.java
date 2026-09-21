package tacos.web.api;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection="idempotency_records")
@CompoundIndex(def = "{'userId': 1, 'idempotencyKey': 1}", unique = true)
public class IdempotencyRecord {
    @Id
    private String id;
    private String userId;
    private String idempotencyKey;
    private String requestHash;
    private String orderId;
    private String status; // IN_PROGRESS, COMPLETED, FAILED
    private Instant createdAt;
}
