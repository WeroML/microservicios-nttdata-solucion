package tacos.inventory;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.dao.OptimisticLockingFailureException;
import tacos.data.IngredientRepository;
import tacos.Ingredient;
import java.util.Map;
import java.util.HashMap;

@Service
public class InventoryService {

    private final IngredientRepository repo;

    public InventoryService(IngredientRepository repo) {
        this.repo = repo;
    }

    public Mono<Void> reserveStock(Map<String, Integer> reservations) {
        return Flux.fromIterable(reservations.entrySet())
            .flatMap(entry -> reserveSingleIngredient(entry.getKey(), entry.getValue()))
            .then();
    }

    private Mono<Void> reserveSingleIngredient(String ingredientId, int amount) {
        return repo.findById(ingredientId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Ingredient not found: " + ingredientId)))
            .flatMap(ingredient -> {
                if (!ingredient.isAvailable()) {
                    return Mono.error(new IllegalStateException("Ingredient is not available for sale: " + ingredientId));
                }
                if (ingredient.getStockOnHand() < amount) {
                    return Mono.error(new IllegalStateException("Insufficient stock for ingredient: " + ingredientId));
                }
                ingredient.setStockOnHand(ingredient.getStockOnHand() - amount);
                return repo.save(ingredient);
            })
            .retryWhen(reactor.util.retry.Retry.max(3).filter(e -> e instanceof OptimisticLockingFailureException))
            .then();
    }

    public Mono<Void> releaseStock(Map<String, Integer> releases) {
        return Flux.fromIterable(releases.entrySet())
            .flatMap(entry -> releaseSingleIngredient(entry.getKey(), entry.getValue()))
            .then();
    }

    private Mono<Void> releaseSingleIngredient(String ingredientId, int amount) {
        return repo.findById(ingredientId)
            .flatMap(ingredient -> {
                ingredient.setStockOnHand(ingredient.getStockOnHand() + amount);
                return repo.save(ingredient);
            })
            .retryWhen(reactor.util.retry.Retry.max(3).filter(e -> e instanceof OptimisticLockingFailureException))
            .then();
    }
}
