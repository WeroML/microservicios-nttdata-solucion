package tacos.validation;

import org.springframework.stereotype.Component;
import tacos.Taco;
import java.util.List;
import java.util.Collections;

@Component
public class IngredientCountRule implements TacoRule {
    @Override
    public List<String> validate(Taco taco) {
        if (taco.getIngredients() == null) {
            return Collections.singletonList("A taco must have ingredients.");
        }
        
        int size = taco.getIngredients().size();
        if (size < 2) {
            return Collections.singletonList("A taco must have at least 2 ingredients.");
        }
        if (size > 12) {
            return Collections.singletonList("A taco cannot exceed 12 ingredients.");
        }
        
        return Collections.emptyList();
    }
}
