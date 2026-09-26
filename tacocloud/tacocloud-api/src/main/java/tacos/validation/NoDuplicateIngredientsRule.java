package tacos.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tacos.Taco;

@Component
public class NoDuplicateIngredientsRule implements TacoRule {

    public static final String CODE = "DUPLICATE_INGREDIENT";

    @Override
    public List<RuleViolation> validate(Taco taco) {
        Set<String> seen = new HashSet<>();
        return taco.getIngredients().stream()
            .map(i -> i.getId())
            .filter(id -> !seen.add(id))
            .distinct()
            .map(id -> new RuleViolation(CODE, "Ingredient " + id + " is repeated."))
            .collect(Collectors.toList());
    }
}
