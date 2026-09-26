package tacos.validation;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import tacos.Ingredient;
import tacos.Taco;

/**
 * Exactamente una base: una tortilla (WRAP) o, si no lleva tortilla, un bowl.
 * Por eso el único caso inválido es tener dos o más WRAP.
 */
@Component
public class SingleBaseRule implements TacoRule {

    public static final String CODE = "MULTIPLE_BASES";

    @Override
    public List<RuleViolation> validate(Taco taco) {
        long wraps = taco.getIngredients().stream()
            .filter(i -> i.getType() == Ingredient.Type.WRAP)
            .count();
        if (wraps > 1) {
            return Collections.singletonList(new RuleViolation(CODE,
                "A taco must have exactly one base: one wrap or a bowl (no wrap)."));
        }
        return Collections.emptyList();
    }
}
