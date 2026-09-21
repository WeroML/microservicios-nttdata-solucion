package tacos.classification;

import org.springframework.stereotype.Service;
import tacos.Taco;
import tacos.Ingredient;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.SpiceLevel;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;

@Service
public class ClassificationService {

    public TacoClassification classify(Taco taco) {
        if (taco.getIngredients() == null || taco.getIngredients().isEmpty()) {
            return new TacoClassification(new HashSet<>(), new HashSet<>(), SpiceLevel.NONE);
        }

        Set<DietaryTag> tags = EnumSet.allOf(DietaryTag.class);
        Set<Allergen> allergens = EnumSet.noneOf(Allergen.class);
        SpiceLevel maxSpice = SpiceLevel.NONE;

        for (Ingredient i : taco.getIngredients()) {
            if (i.getDietaryTags() != null) {
                tags.retainAll(i.getDietaryTags());
            } else {
                tags.clear();
            }

            if (i.getAllergens() != null) {
                allergens.addAll(i.getAllergens());
            }

            if (i.getSpiceLevel() != null && i.getSpiceLevel().ordinal() > maxSpice.ordinal()) {
                maxSpice = i.getSpiceLevel();
            }
        }

        return new TacoClassification(tags, allergens, maxSpice);
    }

    public static class TacoClassification {
        public final Set<DietaryTag> dietaryTags;
        public final Set<Allergen> allergens;
        public final SpiceLevel spiceLevel;

        public TacoClassification(Set<DietaryTag> dietaryTags, Set<Allergen> allergens, SpiceLevel spiceLevel) {
            this.dietaryTags = dietaryTags;
            this.allergens = allergens;
            this.spiceLevel = spiceLevel;
        }
    }
}
