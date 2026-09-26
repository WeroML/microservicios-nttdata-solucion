package tacos.kitchen;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-30: registro de eventos procesados. El @Id es el eventId, por lo que es único.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "processed_events")
public class ProcessedEvent {
    @Id
    private String eventId;
    private String orderId;
    private String eventType;
    private String result;
    private Instant processedAt;
}
