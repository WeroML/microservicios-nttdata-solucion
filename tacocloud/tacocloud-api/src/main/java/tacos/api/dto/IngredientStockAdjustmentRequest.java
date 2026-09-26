package tacos.api.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class IngredientStockAdjustmentRequest {
    // Positivo agrega stock, negativo lo retira. Nunca puede dejar el stock negativo.
    @NotNull
    private Integer amount;
}
