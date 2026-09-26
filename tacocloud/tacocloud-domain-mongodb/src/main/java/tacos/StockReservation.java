package tacos;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

// TC-16: registro de reserva para no descontar ni liberar dos veces.
@Data
@NoArgsConstructor
@Document(collection = "stock_reservations")
public class StockReservation {

    @Id
    private String id; // llave de idempotencia de la reserva
    @Indexed
    private String orderId;
    private Map<String, Integer> quantities = new LinkedHashMap<>();
    private Status status;
    private Instant createdAt;
    private Instant updatedAt;

    public enum Status {
        RESERVING, RESERVED, RELEASED, FAILED
    }
}
