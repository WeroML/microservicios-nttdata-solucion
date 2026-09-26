package tacos.api.dto;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;

import lombok.Data;

@Data
public class IngredientCatalogPatchRequest {
    @DecimalMin("0.00")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal unitPrice;
    private Boolean available;
}
