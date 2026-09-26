package tacos;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-25: quién, cuándo, origen y razón de cada cambio. Sin datos sensibles.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistory {
    private OrderStatus status;
    private Instant changedAt;
    private String changedBy;
    private String origin;
    private String reason;
}
