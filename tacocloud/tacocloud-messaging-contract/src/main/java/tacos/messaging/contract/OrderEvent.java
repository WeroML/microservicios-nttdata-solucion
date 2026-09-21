package tacos.messaging.contract;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String eventId = UUID.randomUUID().toString();
    private OrderEventType eventType;
    private String version = "v1";
    private Instant occurredAt = Instant.now();
    private String correlationId;
    
    private OrderEventPayload payload;
}
