package tacos.web.api;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.api.dto.ClassificationResponse;
import tacos.api.dto.PageResponse;
import tacos.api.dto.TacoMapper;
import tacos.api.dto.TacoOfTheDayResponse;
import tacos.api.dto.TacoRequest;
import tacos.api.dto.TacoResponse;
import tacos.api.error.BadRequestException;
import tacos.api.error.NotFoundException;
import tacos.classification.ClassificationService;
import tacos.data.TacoRepository;
import tacos.data.TacoSearchCriteria;
import tacos.validation.TacoDesignService;

@RestController
@RequestMapping(path={"/api/v1/tacos", "/api/tacos"}, produces="application/json")
public class TacoController {

  private final TacoRepository tacoRepo;
  private final TacoDesignService designService;
  private final ClassificationService classificationService;
  private final TacoOfTheDayService tacoOfTheDayService;
  private final TacoMapper tacoMapper;
  private final TacoSearchProperties searchProperties;

  public TacoController(TacoRepository tacoRepo, TacoDesignService designService,
                        ClassificationService classificationService, TacoOfTheDayService tacoOfTheDayService,
                        TacoMapper tacoMapper, TacoSearchProperties searchProperties) {
    this.tacoRepo = tacoRepo;
    this.designService = designService;
    this.classificationService = classificationService;
    this.tacoOfTheDayService = tacoOfTheDayService;
    this.tacoMapper = tacoMapper;
    this.searchProperties = searchProperties;
  }

  /**
   * TC-19: GET /api/v1/tacos?name=&ingredientId=&diet=&excludeAllergen=&spice=&page=0&size=20&sort=createdAt,desc
   * Filtros opcionales combinados como intersección; sort con lista blanca y
   * desempate por ID para que la paginación sea estable.
   */
  @GetMapping
  public Mono<PageResponse<TacoResponse>> searchTacos(
      @RequestParam(required=false) String name,
      @RequestParam(required=false) String ingredientId,
      @RequestParam(required=false) Ingredient.DietaryTag diet,
      @RequestParam(required=false) List<Ingredient.Allergen> excludeAllergen,
      @RequestParam(required=false) Ingredient.SpiceLevel spice,
      @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="createdAt,desc") String sort) {

    Paging.validate(page, size, searchProperties.getMaxPageSize());
    if (name != null && name.length() > searchProperties.getMaxNameLength()) {
      throw new BadRequestException("SEARCH_TEXT_TOO_LONG",
          "name must be at most " + searchProperties.getMaxNameLength() + " characters.");
    }

    TacoSearchCriteria criteria = TacoSearchCriteria.builder()
        .name(name)
        .ingredientId(ingredientId)
        .diet(diet)
        .excludeAllergens(excludeAllergen == null ? Collections.emptyList() : excludeAllergen)
        .spice(spice)
        .build();
    // Se pide un elemento extra para saber si existe la página siguiente.
    return tacoRepo.searchTacos(criteria, parseSort(sort), (long) page * size, size + 1)
        .map(tacoMapper::toResponse)
        .collectList()
        .map(list -> PageResponse.of(list, page, size));
  }

  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    String field = parts[0].trim();
    if (!searchProperties.getSortableFields().contains(field)) {
      throw new BadRequestException("INVALID_SORT",
          "sort must be one of " + searchProperties.getSortableFields() + ".");
    }
    Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
        ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(new Sort.Order(direction, field), Sort.Order.asc("_id"));
  }

  // TC-20: 200 con el taco del día o 404 NO_TACO_OF_THE_DAY si no hay candidatos.
  @GetMapping("/today")
  public Mono<TacoOfTheDayResponse> getTacoOfDay() {
    return tacoOfTheDayService.getTacoOfTheDay()
        .switchIfEmpty(Mono.error(() -> new NotFoundException("NO_TACO_OF_THE_DAY",
            "There are no available tacos to recommend today.")));
  }

  // TC-17/TC-18: el diseño se resuelve contra el catálogo y se valida antes de guardar.
  @PostMapping(consumes="application/json")
  public Mono<ResponseEntity<TacoResponse>> postTaco(@Valid @RequestBody TacoRequest request,
                                                     UriComponentsBuilder uriBuilder) {
    return designService.design(request)
        .flatMap(tacoRepo::save)
        .map(saved -> {
          URI location = uriBuilder.path("/api/v1/tacos/{id}").buildAndExpand(saved.getId()).toUri();
          return ResponseEntity.created(location).body(tacoMapper.toResponse(saved));
        });
  }

  @GetMapping("/{id}")
  public Mono<TacoResponse> tacoById(@PathVariable("id") String id) {
    return findTaco(id).map(tacoMapper::toResponse);
  }

  // TC-17: clasificación derivada de los ingredientes + disclaimer académico.
  @GetMapping("/{id}/classification")
  public Mono<ClassificationResponse> classifyTaco(@PathVariable("id") String id) {
    return findTaco(id).map(classificationService::toResponse);
  }

  private Mono<tacos.Taco> findTaco(String id) {
    return tacoRepo.findById(id)
        .switchIfEmpty(Mono.error(() -> new NotFoundException("TACO_NOT_FOUND", "Taco " + id + " not found.")));
  }
}
