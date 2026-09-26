package tacos.api.dto;

import java.util.Date;
import java.util.List;
import java.util.Set;

import lombok.Data;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;

@Data
public class TacoResponse {
    private String id;
    private String name;
    private Date createdAt;
    private List<IngredientResponse> ingredients;
    private Set<DietaryTag> dietaryTags;
    private Set<Allergen> allergens;
    private SpiceLevel spiceLevel;
}
