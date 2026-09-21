package tacos.validation;

import org.springframework.stereotype.Component;
import tacos.Taco;
import tacos.Ingredient;
import java.util.List;
import java.util.Collections;

@Component
public class GhostPepperRule implements TacoRule {
    @Override
    public List<String> validate(Taco taco) {
        if (taco.getIngredients() == null) return Collections.emptyList();
        
        boolean hasGhostPepper = taco.getIngredients().stream()
            .anyMatch(i -> i.getSpiceLevel() == Ingredient.SpiceLevel.GHOST_PEPPER);
            
        if (hasGhostPepper) {
            // Suppose SAUCE type isn't enough, we strictly require a Drink...
            // Or maybe just outputting a fun rule for domain!
            return Collections.singletonList("Ghost Pepper demands a signed waiver or a drink (not yet implemented in catalog)!");
        }
        
        return Collections.emptyList();
    }
}
