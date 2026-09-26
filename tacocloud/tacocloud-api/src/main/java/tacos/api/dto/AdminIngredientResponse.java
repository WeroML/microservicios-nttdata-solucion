package tacos.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

// TC-13: vista para ADMIN con los datos operativos.
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminIngredientResponse extends IngredientResponse {
    private int stockOnHand;
    private int reorderLevel;
    private Long version;
}
