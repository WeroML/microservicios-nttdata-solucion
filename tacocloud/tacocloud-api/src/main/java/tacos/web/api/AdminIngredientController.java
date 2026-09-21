package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tacos.api.dto.IngredientCatalogPatchRequest;
import tacos.api.dto.IngredientStockAdjustmentRequest;
import tacos.api.dto.IngredientResponse;
import tacos.api.dto.IngredientMapper;
import tacos.data.IngredientRepository;

import javax.validation.Valid;

@RestController
@RequestMapping(path="/api/v1/admin/ingredients", produces="application/json")
public class AdminIngredientController {

    private final IngredientRepository repo;
    private final IngredientMapper mapper;

    public AdminIngredientController(IngredientRepository repo, IngredientMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @PatchMapping("/{id}/catalog")
    public Mono<ResponseEntity<IngredientResponse>> updateCatalog(@PathVariable String id, @Valid @RequestBody IngredientCatalogPatchRequest request) {
        return repo.findById(id)
            .flatMap(existing -> {
                if (request.getUnitPrice() != null) {
                    existing.setUnitPrice(request.getUnitPrice());
                }
                if (request.getAvailable() != null) {
                    existing.setAvailable(request.getAvailable());
                }
                return repo.save(existing);
            })
            .map(saved -> ResponseEntity.ok(mapper.toResponse(saved)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/stock-adjustments")
    public Mono<ResponseEntity<IngredientResponse>> adjustStock(@PathVariable String id, @Valid @RequestBody IngredientStockAdjustmentRequest request) {
        return repo.findById(id)
            .flatMap(existing -> {
                int newStock = existing.getStockOnHand() + request.getAmount();
                if (newStock < 0) {
                    return Mono.error(new IllegalArgumentException("Stock cannot be negative"));
                }
                existing.setStockOnHand(newStock);
                return repo.save(existing);
            })
            .map(saved -> ResponseEntity.ok(mapper.toResponse(saved)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
