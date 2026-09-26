package tacos.data;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Value;
import tacos.Ingredient;

// TC-19: filtros opcionales de búsqueda; todos se combinan como intersección.
@Value
@Builder
public class TacoSearchCriteria {
    String name;
    String ingredientId;
    Ingredient.DietaryTag diet;
    @Builder.Default
    List<Ingredient.Allergen> excludeAllergens = Collections.emptyList();
    Ingredient.SpiceLevel spice;
}
