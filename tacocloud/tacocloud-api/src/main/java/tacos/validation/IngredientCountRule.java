package tacos.validation;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import tacos.Taco;

@Component
public class IngredientCountRule implements TacoRule {

    public static final String CODE = "INGREDIENT_COUNT";
    static final int MIN = 2;
    static final int MAX = 12;

    @Override
    public List<RuleViolation> validate(Taco taco) {
        int size = taco.getIngredients().size();
        if (size < MIN || size > MAX) {
            return Collections.singletonList(new RuleViolation(CODE,
                "A taco must have between " + MIN + " and " + MAX + " ingredients."));
        }
        return Collections.emptyList();
    }
}
