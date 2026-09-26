package tacos.classification;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import tacos.Ingredient;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;
import tacos.Taco;
import tacos.api.dto.ClassificationResponse;

/**
 * TC-17: la clasificación del taco se deriva de sus ingredientes.
 *
 * Datos del ingrediente: etiquetas dietarias, alérgenos y nivel de picante.
 * Políticas del producto (derivadas):
 * - Una etiqueta (VEGAN, VEGETARIAN, GLUTEN_FREE) sólo es verdadera si TODOS
 *   los ingredientes la tienen (intersección).
 * - Los alérgenos del taco son la unión exacta de los alérgenos de sus ingredientes.
 * - El picante del taco es el mayor picante entre sus ingredientes.
 */
@Service
public class ClassificationService {

    public static final String DISCLAIMER =
        "Academic metadata only: it does not replace real cross-contamination control.";

    public TacoClassification classify(List<Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return new TacoClassification(EnumSet.noneOf(DietaryTag.class),
                EnumSet.noneOf(Allergen.class), SpiceLevel.NONE);
        }

        Set<DietaryTag> tags = EnumSet.allOf(DietaryTag.class);
        Set<Allergen> allergens = EnumSet.noneOf(Allergen.class);
        SpiceLevel maxSpice = SpiceLevel.NONE;

        for (Ingredient i : ingredients) {
            tags.retainAll(i.getDietaryTags());
            allergens.addAll(i.getAllergens());
            if (i.getSpiceLevel().compareTo(maxSpice) > 0) {
                maxSpice = i.getSpiceLevel();
            }
        }

        return new TacoClassification(tags, allergens, maxSpice);
    }

    // Sobrescribe cualquier valor previo: el cliente nunca decide la clasificación.
    public void applyTo(Taco taco) {
        TacoClassification c = classify(taco.getIngredients());
        taco.setDietaryTags(c.dietaryTags);
        taco.setAllergens(c.allergens);
        taco.setSpiceLevel(c.spiceLevel);
    }

    public ClassificationResponse toResponse(Taco taco) {
        TacoClassification c = classify(taco.getIngredients());
        return new ClassificationResponse(c.dietaryTags, c.allergens, c.spiceLevel, DISCLAIMER);
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
