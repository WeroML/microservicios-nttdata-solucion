package tacos.api.dto;

import java.util.HashSet;

import org.springframework.stereotype.Component;

import tacos.Ingredient;

// TC-08: sólo transforma datos ya disponibles; no consulta repositorios ni aplica reglas.
@Component
public class IngredientMapper {

    public Ingredient toNewDomain(IngredientRequest req) {
        Ingredient i = new Ingredient(null, req.getName(), req.getType());
        i.setUnitPrice(req.getUnitPrice());
        i.setAvailable(req.isAvailable());
        i.setStockOnHand(req.getStockOnHand());
        i.setReorderLevel(req.getReorderLevel());
        return i;
    }

    // PUT: copia los campos editables sobre el documento existente.
    // stockOnHand no se toca: se ajusta con stock-adjustments.
    public void applyUpdate(Ingredient existing, IngredientRequest req) {
        existing.setName(req.getName());
        existing.setType(req.getType());
        existing.setUnitPrice(req.getUnitPrice());
        existing.setAvailable(req.isAvailable());
        existing.setReorderLevel(req.getReorderLevel());
        if (req.getVersion() != null) {
            existing.setVersion(req.getVersion());
        }
    }

    public IngredientResponse toResponse(Ingredient ingredient) {
        IngredientResponse resp = new IngredientResponse();
        fill(resp, ingredient);
        return resp;
    }

    public AdminIngredientResponse toAdminResponse(Ingredient ingredient) {
        AdminIngredientResponse resp = new AdminIngredientResponse();
        fill(resp, ingredient);
        resp.setStockOnHand(ingredient.getStockOnHand());
        resp.setReorderLevel(ingredient.getReorderLevel());
        resp.setVersion(ingredient.getVersion());
        return resp;
    }

    private void fill(IngredientResponse resp, Ingredient ingredient) {
        resp.setId(ingredient.getId());
        resp.setName(ingredient.getName());
        resp.setType(ingredient.getType());
        resp.setUnitPrice(ingredient.getUnitPrice());
        resp.setAvailable(ingredient.isAvailable());
        resp.setDietaryTags(new HashSet<>(ingredient.getDietaryTags()));
        resp.setAllergens(new HashSet<>(ingredient.getAllergens()));
        resp.setSpiceLevel(ingredient.getSpiceLevel());
    }
}
