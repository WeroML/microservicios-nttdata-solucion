package tacos.validation;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.api.dto.ApiProblem;
import tacos.api.dto.TacoRequest;
import tacos.api.error.BusinessRuleException;
import tacos.classification.ClassificationService;
import tacos.data.IngredientRepository;

/**
 * Punto único para convertir un diseño del cliente en un Taco confiable.
 * Lo usan crear taco, validar, cotizar, crear orden, orden por correo y
 * reordenar (TC-18: las mismas reglas en create y quote).
 */
@Service
public class TacoDesignService {

    public static final String UNKNOWN_INGREDIENT = "UNKNOWN_INGREDIENT";
    public static final String INVALID_DESIGN = "INVALID_TACO_DESIGN";

    private final IngredientRepository ingredientRepo;
    private final TacoValidatorService validator;
    private final ClassificationService classificationService;

    public TacoDesignService(IngredientRepository ingredientRepo,
                             TacoValidatorService validator,
                             ClassificationService classificationService) {
        this.ingredientRepo = ingredientRepo;
        this.validator = validator;
        this.classificationService = classificationService;
    }

    // Resuelve, valida y clasifica. Un diseño inválido termina en 422 con todas las violaciones.
    public Mono<Taco> design(String name, List<String> ingredientIds) {
        return resolve(name, ingredientIds)
            .flatMap(taco -> {
                List<RuleViolation> violations = validator.validate(taco);
                if (!violations.isEmpty()) {
                    return Mono.error(invalidDesign(violations));
                }
                classificationService.applyTo(taco);
                return Mono.just(taco);
            });
    }

    public Mono<Taco> design(TacoRequest request) {
        return design(request.getName(), request.getIngredientIds());
    }

    public Mono<List<RuleViolation>> validate(TacoRequest request) {
        return resolve(request.getName(), request.getIngredientIds())
            .map(validator::validate);
    }

    // Los ingredientes siempre salen del catálogo, nunca del body del cliente.
    // concatMap conserva el orden en que el cliente los eligió.
    private Mono<Taco> resolve(String name, List<String> ingredientIds) {
        return Flux.fromIterable(ingredientIds)
            .concatMap(id -> ingredientRepo.findById(id)
                .switchIfEmpty(Mono.error(() -> new BusinessRuleException(UNKNOWN_INGREDIENT,
                    "Unknown ingredient: " + id))))
            .collectList()
            .map(ingredients -> newTaco(name, ingredients));
    }

    private static Taco newTaco(String name, List<Ingredient> ingredients) {
        Taco taco = new Taco();
        taco.setName(name);
        taco.setIngredients(ingredients);
        return taco;
    }

    private static BusinessRuleException invalidDesign(List<RuleViolation> violations) {
        List<ApiProblem.Violation> problems = violations.stream()
            .map(v -> ApiProblem.Violation.builder().code(v.getCode()).message(v.getMessage()).build())
            .collect(Collectors.toList());
        return new BusinessRuleException(INVALID_DESIGN, "The taco design breaks one or more rules.", problems);
    }
}
