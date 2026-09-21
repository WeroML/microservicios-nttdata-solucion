package tacos.api.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import javax.validation.Valid;
import lombok.Data;

@Data
public class OrderCreateRequest {
    @NotBlank
    private String deliveryName;
    @NotBlank
    private String deliveryStreet;
    @NotBlank
    private String deliveryCity;
    @NotBlank
    private String deliveryState;
    @NotBlank
    private String deliveryZip;
    
    @NotBlank
    private String paymentMethodId;
    private String discountCode;
    
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        private TacoRequest taco;
        private Integer quantity;
    }
    
    @Data
    public static class TacoRequest {
        @NotBlank
        @Size(min=5)
        private String name;
        
        @NotEmpty
        private List<String> ingredients;
    }
}
