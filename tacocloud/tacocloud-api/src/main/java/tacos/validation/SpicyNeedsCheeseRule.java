package tacos.validation;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import tacos.Ingredient;
import tacos.Taco;

/**
 * Regla divertida: un taco muy picante exige queso para apagar el fuego.
 * El umbral de picante se configura en tacocloud.taco-rules.spicy-needs-cheese.
 */
@Component
public class SpicyNeedsCheeseRule implements TacoRule {

    public static final String CODE = "SPICY_NEEDS_CHEESE";

    private final TacoRulesProperties properties;

    public SpicyNeedsCheeseRule(TacoRulesProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RuleViolation> validate(Taco taco) {
        TacoRulesProperties.SpicyNeedsCheese config = properties.getSpicyNeedsCheese();
        if (!config.isEnabled()) {
            return Collections.emptyList();
        }
        boolean veryHot = taco.getIngredients().stream()
            .anyMatch(i -> i.getSpiceLevel().compareTo(config.getMinSpiceLevel()) >= 0);
        boolean hasCheese = taco.getIngredients().stream()
            .anyMatch(i -> i.getType() == Ingredient.Type.CHEESE);
        if (veryHot && !hasCheese) {
            return Collections.singletonList(new RuleViolation(CODE,
                "A taco at spice level " + config.getMinSpiceLevel() + " or above needs cheese."));
        }
        return Collections.emptyList();
    }
}
