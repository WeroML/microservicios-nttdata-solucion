package tacos.api.dto;

import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class OrderResponse {
    private String id;
    private Date placedAt;
    private String deliveryName;
    private String deliveryStreet;
    private String deliveryCity;
    private String deliveryState;
    private String deliveryZip;
    private String paymentMethodId;
    private String discountCode;
    private java.math.BigDecimal discountAmount;
    private String status;
    private java.util.List<StatusHistoryResponse> statusHistory;

    @Data
    public static class StatusHistoryResponse {
        private String status;
        private java.time.Instant changedAt;
        private String changedBy;
        private String reason;
    }

    private List<OrderItemResponse> items;
    private java.math.BigDecimal total;

    @Data
    public static class OrderItemResponse {
        private TacoResponse taco;
        private Integer quantity;
        private java.math.BigDecimal unitPriceAtPurchase;
        private java.math.BigDecimal subtotal;
    }

    @Data
    public static class TacoResponse {
        private String id;
        private String name;
        private Date createdAt;
        private List<IngredientResponse> ingredients;
    }
}
