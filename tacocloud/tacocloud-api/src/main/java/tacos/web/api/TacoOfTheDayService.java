package tacos.web.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.api.dto.TacoMapper;
import tacos.api.dto.TacoOfTheDayResponse;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;
import tacos.validation.TacoValidatorService;

/**
 * TC-20: taco del día determinista.
 * - Fecha tomada del Clock inyectado con la zona configurada (tacocloud.clock.zone).
 * - Candidatos: tacos cuyos ingredientes existen y están disponibles HOY en el
 *   catálogo y que cumplen las reglas de diseño. Si un ingrediente deja de estar
 *   disponible, el taco deja de ser candidato en la siguiente consulta (sin caché).
 * - Candidatos ordenados por ID; índice = epochDay mod cantidad. La misma fecha y el
 *   mismo catálogo dan el mismo taco en cualquier instancia, sin importar el orden físico.
 * - Sin candidatos: vacío (el controlador responde 404). No se persiste nada.
 */
@Service
public class TacoOfTheDayService {

    private final TacoRepository tacoRepo;
    private final IngredientRepository ingredientRepo;
    private final TacoValidatorService validator;
    private final TacoMapper tacoMapper;
    private final Clock clock;

    public TacoOfTheDayService(TacoRepository tacoRepo, IngredientRepository ingredientRepo,
                               TacoValidatorService validator, TacoMapper tacoMapper, Clock clock) {
        this.tacoRepo = tacoRepo;
        this.ingredientRepo = ingredientRepo;
        this.validator = validator;
        this.tacoMapper = tacoMapper;
        this.clock = clock;
    }

    public Mono<TacoOfTheDayResponse> getTacoOfTheDay() {
        LocalDate today = LocalDate.now(clock);
        return Mono.zip(tacoRepo.findAll().collectList(), ingredientRepo.findAll().collectMap(Ingredient::getId))
            .flatMap(t -> {
                List<Taco> candidates = t.getT1().stream()
                    .map(taco -> withCurrentIngredients(taco, t.getT2()))
                    .filter(taco -> taco != null && validator.validate(taco).isEmpty())
                    .sorted(Comparator.comparing(Taco::getId))
                    .collect(Collectors.toList());
                if (candidates.isEmpty()) {
                    return Mono.empty();
                }
                int index = (int) Math.floorMod(today.toEpochDay(), (long) candidates.size());
                Taco chosen = candidates.get(index);
                String reason = "Chosen because today is " + today.getDayOfWeek() + " " + today
                    + ": taco " + (index + 1) + " of " + candidates.size() + " available.";
                return Mono.just(new TacoOfTheDayResponse(tacoMapper.toResponse(chosen), today.toString(), reason));
            });
    }

    // Reemplaza los snapshots guardados por el estado actual del catálogo; null si falta alguno.
    private static Taco withCurrentIngredients(Taco taco, Map<String, Ingredient> catalog) {
        if (taco.getIngredients() == null || taco.getIngredients().isEmpty()) {
            return null;
        }
        List<Ingredient> current = taco.getIngredients().stream()
            .map(i -> catalog.get(i.getId()))
            .collect(Collectors.toList());
        if (current.contains(null)) {
            return null;
        }
        Taco copy = new Taco();
        copy.setId(taco.getId());
        copy.setName(taco.getName());
        copy.setCreatedAt(taco.getCreatedAt());
        copy.setIngredients(current);
        copy.setDietaryTags(taco.getDietaryTags());
        copy.setAllergens(taco.getAllergens());
        copy.setSpiceLevel(taco.getSpiceLevel());
        return copy;
    }
}
