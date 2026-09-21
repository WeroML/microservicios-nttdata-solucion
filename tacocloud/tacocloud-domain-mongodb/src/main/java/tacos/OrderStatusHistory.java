package tacos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistory {
    private OrderStatus status;
    private Instant changedAt;
    private String changedBy;
    private String reason;
}
