package tacos.api.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;
import tacos.OrderStatus;

@Data
public class OrderStatusUpdateRequest {
    @NotNull
    private OrderStatus status;
    @Size(max = 200)
    private String reason;
}
