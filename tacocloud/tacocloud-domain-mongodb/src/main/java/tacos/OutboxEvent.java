package tacos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_events")
public class OutboxEvent {
    @Id
    private String id; // same as eventId
    private String aggregateId;
    private String aggregateType;
    private String type;
    private Object payload; // The actual event payload
    
    private OutboxStatus status = OutboxStatus.NEW;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private int retries = 0;
    
    public enum OutboxStatus {
        NEW, PUBLISHING, PUBLISHED, FAILED
    }
}
