package tacos.web.api;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.data.IngredientRepository;

@RestController
@RequestMapping(path="/api/v1/ingredients", produces="application/json")
@CrossOrigin(origins="http://localhost:8080")
public class IngredientController {

  private IngredientRepository repo;
  private tacos.api.dto.IngredientMapper mapper;

  @Autowired
  public IngredientController(IngredientRepository repo, tacos.api.dto.IngredientMapper mapper) {
    this.repo = repo;
    this.mapper = mapper;
  }

  @GetMapping
  public Flux<tacos.api.dto.IngredientResponse> allIngredients() {
    return repo.findAll().map(mapper::toResponse);
  }

  @GetMapping("/{id}")
  public Mono<tacos.api.dto.IngredientResponse> byId(@PathVariable String id) {
    return repo.findById(id).map(mapper::toResponse);
  }

  @PutMapping("/{id}")
  public Mono<ResponseEntity<tacos.api.dto.IngredientResponse>> updateIngredient(@PathVariable String id, @RequestBody tacos.api.dto.IngredientRequest req) {
    if (req.getId() == null || !req.getId().equals(id)) {
      return Mono.just(ResponseEntity.badRequest().build());
    }
    return repo.findById(id)
        .flatMap(existing -> repo.save(mapper.toDomain(req)))
        .map(saved -> ResponseEntity.ok(mapper.toResponse(saved)))
        .defaultIfEmpty(ResponseEntity.notFound().build());
  }

  @PostMapping
  public Mono<ResponseEntity<tacos.api.dto.IngredientResponse>> postIngredient(
      @RequestBody Mono<tacos.api.dto.IngredientRequest> reqMono,
      org.springframework.http.server.reactive.ServerHttpRequest request) {
    return reqMono
        .map(mapper::toDomain)
        .flatMap(repo::save)
        .map(i -> {
          HttpHeaders headers = new HttpHeaders();
          headers.setLocation(
              org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI())
                  .pathSegment(i.getId())
                  .build().toUri()
          );
          return new ResponseEntity<tacos.api.dto.IngredientResponse>(mapper.toResponse(i), headers, HttpStatus.CREATED);
        });
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> deleteIngredient(@PathVariable String id) {
    return repo.findById(id)
        .flatMap(existing -> repo.deleteById(id)
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT))))
        .defaultIfEmpty(ResponseEntity.notFound().build());
  }

}
