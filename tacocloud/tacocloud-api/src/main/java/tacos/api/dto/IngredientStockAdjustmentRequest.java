package tacos.api.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IngredientStockAdjustmentRequest {
    @NotNull
    private Integer amount; // Can be negative for subtraction or positive for addition
}
