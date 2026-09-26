package tacos.messaging.contract;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TC-27: payload seguro. Contiene los snapshots que la cocina necesita,
 * sin dirección completa, sin usuario y sin datos de pago.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEventPayload {
    private String orderId;
    private String status;
    private String deliveryCity;
    private String deliveryState;
    private BigDecimal total;
    private String currency;

    private List<OrderItemPayload> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItemPayload {
        private String tacoName;
        private List<String> ingredients;
        private Integer quantity;
    }
}
