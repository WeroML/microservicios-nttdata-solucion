package tacos.api.dto;

import lombok.Data;
import tacos.OrderStatus;

@Data
public class OrderStatusUpdateRequest {
    private OrderStatus status;
    private String reason;
}
