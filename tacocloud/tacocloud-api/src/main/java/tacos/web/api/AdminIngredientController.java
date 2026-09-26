package tacos.web.api;

import javax.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.AdminIngredientResponse;
import tacos.api.dto.IngredientCatalogPatchRequest;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.IngredientStockAdjustmentRequest;
import tacos.api.error.NotFoundException;
import tacos.data.IngredientRepository;
import tacos.inventory.InventoryService;

// TC-13: operaciones explícitas de ADMIN sobre precio, disponibilidad y stock.
@RestController
@RequestMapping(path={"/api/v1/admin/ingredients", "/api/admin/ingredients"}, produces="application/json")
@PreAuthorize("hasRole('ADMIN')")
public class AdminIngredientController {

    private final IngredientRepository repo;
    private final IngredientMapper mapper;
    private final InventoryService inventoryService;

    public AdminIngredientController(IngredientRepository repo, IngredientMapper mapper,
                                     InventoryService inventoryService) {
        this.repo = repo;
        this.mapper = mapper;
        this.inventoryService = inventoryService;
    }

    // @Version detecta ediciones concurrentes: el perdedor recibe 409.
    @PatchMapping(path="/{id}/catalog", consumes="application/json")
    public Mono<AdminIngredientResponse> updateCatalog(@PathVariable("id") String id,
                                                       @Valid @RequestBody IngredientCatalogPatchRequest request) {
        return repo.findById(id)
            .switchIfEmpty(Mono.error(() -> new NotFoundException("INGREDIENT_NOT_FOUND",
                "Ingredient " + id + " not found.")))
            .flatMap(existing -> {
                if (request.getUnitPrice() != null) {
                    existing.setUnitPrice(request.getUnitPrice());
                }
                if (request.getAvailable() != null) {
                    existing.setAvailable(request.getAvailable());
                }
                InventoryService.checkCatalogConsistency(existing);
                return repo.save(existing);
            })
            .map(mapper::toAdminResponse);
    }

    // Ajuste atómico: nunca deja stock negativo (422 NEGATIVE_STOCK).
    @PostMapping(path="/{id}/stock-adjustments", consumes="application/json")
    public Mono<AdminIngredientResponse> adjustStock(@PathVariable("id") String id,
                                                     @Valid @RequestBody IngredientStockAdjustmentRequest request) {
        return inventoryService.adjustStock(id, request.getAmount())
            .map(mapper::toAdminResponse);
    }
}
