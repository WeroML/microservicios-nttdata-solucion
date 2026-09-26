package tacos.validation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tacos.Ingredient;
import tacos.Taco;

/**
 * Regla divertida: si el nombre promete "vegan", no admite carnitas ni ningún
 * ingrediente sin la etiqueta VEGAN. La palabra clave es configurable.
 */
@Component
public class VeganNameRule implements TacoRule {

    public static final String CODE = "VEGAN_NAME_NOT_VEGAN";

    private final TacoRulesProperties properties;

    public VeganNameRule(TacoRulesProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RuleViolation> validate(Taco taco) {
        TacoRulesProperties.VeganName config = properties.getVeganName();
        if (!config.isEnabled() || taco.getName() == null) {
            return Collections.emptyList();
        }
        String keyword = config.getKeyword().toLowerCase(Locale.ROOT);
        boolean promisesVegan = taco.getName().toLowerCase(Locale.ROOT).contains(keyword);
        boolean allVegan = taco.getIngredients().stream()
            .allMatch(i -> i.getDietaryTags().contains(Ingredient.DietaryTag.VEGAN));
        if (promisesVegan && !allVegan) {
            return Collections.singletonList(new RuleViolation(CODE,
                "A taco named '" + keyword + "' must only contain vegan ingredients."));
        }
        return Collections.emptyList();
    }
}
