package tacos.kitchen;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import tacos.messaging.contract.OrderEventPayload;

/**
 * TC-30: vista de la cocina construida a partir de los eventos.
 * processedEventIds vive en el mismo documento que el estado: aplicar el cambio
 * y marcar el evento como procesado es una sola escritura atómica.
 */
@Data
@Document(collection = "kitchen_tickets")
public class KitchenTicket {
    @Id
    private String orderId;
    private String status;
    private List<OrderEventPayload.OrderItemPayload> items;
    private Instant receivedAt;
    private Instant updatedAt;
    private Set<String> processedEventIds = new HashSet<>();
}
