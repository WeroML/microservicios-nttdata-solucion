package tacos.api.dto;

import java.util.Date;
import java.util.List;

import lombok.Data;

// TC-26: lo que la cocina necesita; sin dirección completa, usuario ni pago.
@Data
public class KitchenOrderResponse {
    private String id;
    private Date placedAt;
    private String status;
    private String stationId;
    private String cookId;
    private Integer estimatedPrepMinutes;
    private List<Item> items;

    @Data
    public static class Item {
        private String tacoName;
        private List<String> ingredients;
        private int quantity;
    }
}
