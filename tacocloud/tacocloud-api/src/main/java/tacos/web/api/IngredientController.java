package tacos.web.api;

import java.net.URI;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.api.dto.AdminIngredientResponse;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.IngredientRequest;
import tacos.api.dto.IngredientResponse;
import tacos.api.error.BadRequestException;
import tacos.api.error.NotFoundException;
import tacos.data.IngredientRepository;
import tacos.inventory.InventoryService;

@RestController
@RequestMapping(path={"/api/v1/ingredients", "/api/ingredients"}, produces="application/json")
public class IngredientController {

  static final String RESOURCE_PATH = "/api/v1/ingredients/{id}";

  private final IngredientRepository repo;
  private final IngredientMapper mapper;

  public IngredientController(IngredientRepository repo, IngredientMapper mapper) {
    this.repo = repo;
    this.mapper = mapper;
  }

  @GetMapping
  public Flux<IngredientResponse> allIngredients() {
    return repo.findAll().map(mapper::toResponse);
  }

  @GetMapping("/{id}")
  public Mono<IngredientResponse> byId(@PathVariable("id") String id) {
    return repo.findById(id)
        .switchIfEmpty(Mono.error(() -> notFound(id)))
        .map(mapper::toResponse);
  }

  /**
   * TC-01: la escritura forma parte de la cadena que se devuelve al framework.
   * 200 con el ingrediente actualizado, 404 si no existe (PUT nunca crea),
   * 400 si el ID del body no coincide con el de la ruta.
   */
  @PutMapping(path="/{id}", consumes="application/json")
  @PreAuthorize("hasRole('ADMIN')")
  public Mono<ResponseEntity<AdminIngredientResponse>> updateIngredient(
      @PathVariable("id") String id, @Valid @RequestBody IngredientRequest req) {
    if (!id.equals(req.getId())) {
      return Mono.error(new BadRequestException("ID_MISMATCH",
          "The ingredient id in the body must match the id in the path."));
    }
    return repo.findById(id)
        .switchIfEmpty(Mono.error(() -> notFound(id)))
        .flatMap(existing -> {
          mapper.applyUpdate(existing, req);
          InventoryService.checkCatalogConsistency(existing);
          return repo.save(existing);
        })
        .map(saved -> ResponseEntity.ok(mapper.toAdminResponse(saved)));
  }

  /**
   * TC-03: 201 + Location construido con la petición actual (esquema, host,
   * puerto y context path; detrás de proxy vía X-Forwarded-*) y la ruta real,
   * usando el ID que asignó la persistencia.
   */
  @PostMapping(consumes="application/json")
  @PreAuthorize("hasRole('ADMIN')")
  public Mono<ResponseEntity<AdminIngredientResponse>> postIngredient(
      @Valid @RequestBody IngredientRequest req, UriComponentsBuilder uriBuilder) {
    if (req.getId() != null) {
      return Mono.error(new BadRequestException("ID_NOT_ALLOWED",
          "The ingredient id is assigned by the server."));
    }
    Ingredient ingredient = mapper.toNewDomain(req);
    InventoryService.checkCatalogConsistency(ingredient);
    return repo.save(ingredient)
        .map(saved -> {
          URI location = uriBuilder.path(RESOURCE_PATH).buildAndExpand(saved.getId()).toUri();
          return ResponseEntity.created(location).body(mapper.toAdminResponse(saved));
        });
  }

  /**
   * TC-02: la búsqueda y el delete están encadenados. 204 sin cuerpo cuando se
   * eliminó; 404 si no existe. Una segunda eliminación del mismo ID responde 404:
   * el efecto (el ingrediente no existe) es el mismo, por eso DELETE sigue siendo idempotente.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Mono<ResponseEntity<Void>> deleteIngredient(@PathVariable("id") String id) {
    return repo.findById(id)
        .switchIfEmpty(Mono.error(() -> notFound(id)))
        .flatMap(existing -> repo.delete(existing))
        .thenReturn(ResponseEntity.noContent().<Void>build());
  }

  private static NotFoundException notFound(String id) {
    return new NotFoundException("INGREDIENT_NOT_FOUND", "Ingredient " + id + " not found.");
  }

}
