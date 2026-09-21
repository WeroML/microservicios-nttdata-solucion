package tacos.messaging.contract;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventPayload {
    private String orderId;
    private String userId; // Or just user identifying info, no sensitive data
    private String deliveryCity;
    private String deliveryState;
    private String deliveryZip;
    // No full street address or payment data
    private String status;
    private BigDecimal total;
    
    private List<OrderItemPayload> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemPayload {
        private String tacoName;
        private List<String> ingredients;
        private Integer quantity;
    }
}
