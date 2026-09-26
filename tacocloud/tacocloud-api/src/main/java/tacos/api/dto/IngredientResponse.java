package tacos.api.dto;

import java.math.BigDecimal;
import java.util.Set;

import lombok.Data;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;
import tacos.Ingredient.Type;

// TC-13/TC-17: vista pública del catálogo, sin stock ni metadatos operativos.
@Data
public class IngredientResponse {
    private String id;
    private String name;
    private Type type;
    private BigDecimal unitPrice;
    private boolean available;
    private Set<DietaryTag> dietaryTags;
    private Set<Allergen> allergens;
    private SpiceLevel spiceLevel;
}
