package tacos.api.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationResponse {
    private Set<DietaryTag> dietaryTags;
    private Set<Allergen> allergens;
    private SpiceLevel spiceLevel;
    private String disclaimer;
}
