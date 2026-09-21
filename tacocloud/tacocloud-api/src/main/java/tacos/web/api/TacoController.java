package tacos.web.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.data.TacoRepository;

@RestController
@RequestMapping(path="/api/v1/tacos", produces="application/json")
@CrossOrigin(origins="http://localhost:8080")
public class TacoController {
  private TacoRepository tacoRepo;
  private tacos.classification.ClassificationService classificationService;
  private tacos.validation.TacoValidatorService validatorService;
  private TacoOfTheDayService tacoOfTheDayService;

  public TacoController(TacoRepository tacoRepo, 
                        tacos.classification.ClassificationService classificationService,
                        tacos.validation.TacoValidatorService validatorService,
                        TacoOfTheDayService tacoOfTheDayService) {
    this.tacoRepo = tacoRepo;
    this.classificationService = classificationService;
    this.validatorService = validatorService;
    this.tacoOfTheDayService = tacoOfTheDayService;
  }

  @GetMapping(params="recent")
  public Flux<Taco> recentTacos() {
    return tacoRepo.findAll().take(12);
  }

  @GetMapping("/today")
  public Mono<org.springframework.http.ResponseEntity<TacoOfTheDayService.TacoOfTheDayResponse>> getTacoOfDay() {
      return tacoOfTheDayService.getTacoOfTheDay()
          .map(org.springframework.http.ResponseEntity::ok)
          .defaultIfEmpty(org.springframework.http.ResponseEntity.notFound().build());
  }

  @GetMapping
  public Flux<Taco> searchTacos(
      @org.springframework.web.bind.annotation.RequestParam(required=false) String name,
      @org.springframework.web.bind.annotation.RequestParam(required=false) String ingredientId,
      @org.springframework.web.bind.annotation.RequestParam(required=false) java.util.List<String> dietaryTags,
      @org.springframework.web.bind.annotation.RequestParam(required=false) java.util.List<String> excludeAllergens,
      @org.springframework.web.bind.annotation.RequestParam(required=false) String spice,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue="createdAt,desc") String sort) {
      
      String[] sortParams = sort.split(",");
      org.springframework.data.domain.Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") ? 
          org.springframework.data.domain.Sort.Direction.DESC : org.springframework.data.domain.Sort.Direction.ASC;
      org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(direction, sortParams[0]));
      
      return tacoRepo.searchTacos(name, ingredientId, dietaryTags, excludeAllergens, spice, pageRequest);
  }

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<Taco> postTaco(@RequestBody Taco taco) {
    java.util.List<String> errors = validatorService.validate(taco);
    if (!errors.isEmpty()) {
        return Mono.error(new IllegalArgumentException("Invalid taco design: " + String.join(", ", errors)));
    }
    
    tacos.classification.ClassificationService.TacoClassification classification = classificationService.classify(taco);
    taco.setDietaryTags(classification.dietaryTags);
    taco.setAllergens(classification.allergens);
    taco.setSpiceLevel(classification.spiceLevel);
    
    return tacoRepo.save(taco);
  }

  @GetMapping("/{id}")
  public Mono<Taco> tacoById(@PathVariable("id") String id) {
    return tacoRepo.findById(id);
  }

  @GetMapping("/{id}/classification")
  public Mono<org.springframework.http.ResponseEntity<tacos.classification.ClassificationService.TacoClassification>> classifyTaco(@PathVariable("id") String id) {
    return tacoRepo.findById(id)
      .map(classificationService::classify)
      .map(org.springframework.http.ResponseEntity::ok)
      .defaultIfEmpty(org.springframework.http.ResponseEntity.notFound().build());
  }
}
