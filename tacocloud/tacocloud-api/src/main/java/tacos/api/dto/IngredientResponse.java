package tacos.api.dto;

import lombok.Data;
import tacos.Ingredient.Type;

@Data
public class IngredientResponse {
    private String id;
    private String name;
    private Type type;
    
    private java.math.BigDecimal unitPrice;
    private boolean available;
}
