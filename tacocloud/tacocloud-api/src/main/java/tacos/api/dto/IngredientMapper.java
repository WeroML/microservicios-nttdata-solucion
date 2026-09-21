package tacos.api.dto;

import org.springframework.stereotype.Component;
import tacos.Ingredient;

@Component
public class IngredientMapper {

    public Ingredient toDomain(IngredientRequest req) {
        if (req == null) return null;
        Ingredient i = new Ingredient(req.getId(), req.getName(), req.getType());
        i.setUnitPrice(req.getUnitPrice());
        i.setAvailable(req.isAvailable());
        i.setStockOnHand(req.getStockOnHand());
        i.setReorderLevel(req.getReorderLevel());
        i.setVersion(req.getVersion());
        return i;
    }

    public IngredientResponse toResponse(Ingredient ingredient) {
        if (ingredient == null) return null;
        IngredientResponse resp = new IngredientResponse();
        resp.setId(ingredient.getId());
        resp.setName(ingredient.getName());
        resp.setType(ingredient.getType());
        resp.setUnitPrice(ingredient.getUnitPrice());
        resp.setAvailable(ingredient.isAvailable());
        return resp;
    }
}
