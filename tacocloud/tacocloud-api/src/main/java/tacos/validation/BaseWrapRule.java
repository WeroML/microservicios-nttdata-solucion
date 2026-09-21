package tacos.validation;

import org.springframework.stereotype.Component;
import tacos.Taco;
import tacos.Ingredient;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class BaseWrapRule implements TacoRule {
    @Override
    public List<String> validate(Taco taco) {
        if (taco.getIngredients() == null) return Collections.emptyList();
        
        long wrapCount = taco.getIngredients().stream()
            .filter(i -> i.getType() == Ingredient.Type.WRAP)
            .count();
            
        if (wrapCount > 1) {
            return Collections.singletonList("A taco can have at most one wrap.");
        }
        return Collections.emptyList();
    }
}
