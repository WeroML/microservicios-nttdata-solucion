package tacos.api.dto;

import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tacos.Taco;

@Component
public class TacoMapper {

    private final IngredientMapper ingredientMapper;

    public TacoMapper(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    public TacoResponse toResponse(Taco taco) {
        TacoResponse resp = new TacoResponse();
        resp.setId(taco.getId());
        resp.setName(taco.getName());
        resp.setCreatedAt(taco.getCreatedAt());
        resp.setIngredients(taco.getIngredients() == null ? Collections.emptyList()
            : taco.getIngredients().stream().map(ingredientMapper::toResponse).collect(Collectors.toList()));
        resp.setDietaryTags(new HashSet<>(taco.getDietaryTags()));
        resp.setAllergens(new HashSet<>(taco.getAllergens()));
        resp.setSpiceLevel(taco.getSpiceLevel());
        return resp;
    }
}
