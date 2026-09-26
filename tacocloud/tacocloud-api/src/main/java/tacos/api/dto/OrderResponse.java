package tacos.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import lombok.Data;

// TC-08/TC-12/TC-23: respuesta segura. Sin User, password, PAN, CVV ni token.
@Data
public class OrderResponse {
    private String id;
    private Date placedAt;
    private String status;
    private String deliveryName;
    private String deliveryStreet;
    private String deliveryCity;
    private String deliveryState;
    private String deliveryZip;
    private PaymentSummary payment;
    private String discountCode;
    private BigDecimal discountAmount;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal total;
    private List<OrderItemResponse> items;
    private List<StatusHistoryResponse> statusHistory;

    @Data
    public static class PaymentSummary {
        private String brand;
        private String last4;
    }

    @Data
    public static class OrderItemResponse {
        private TacoResponse taco;
        private Integer quantity;
        private BigDecimal unitPriceAtPurchase;
        private BigDecimal subtotal;
    }

    @Data
    public static class StatusHistoryResponse {
        private String status;
        private Instant changedAt;
        private String changedBy;
        private String origin;
        private String reason;
    }
}
