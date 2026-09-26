package tacos.validation;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tacos.Taco;

@Component
public class AvailableIngredientsRule implements TacoRule {

    public static final String CODE = "INGREDIENT_UNAVAILABLE";

    @Override
    public List<RuleViolation> validate(Taco taco) {
        return taco.getIngredients().stream()
            .filter(i -> !i.isAvailable())
            .map(i -> new RuleViolation(CODE, "Ingredient " + i.getId() + " is not available."))
            .collect(Collectors.toList());
    }
}
