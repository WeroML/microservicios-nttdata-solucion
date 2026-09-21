package tacos.api.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class IngredientCatalogPatchRequest {
    @DecimalMin("0.0")
    private BigDecimal unitPrice;
    private Boolean available;
}
