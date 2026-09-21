package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Favorite;
import tacos.User;
import tacos.data.FavoriteRepository;
import tacos.data.TacoRepository;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping(path="/api/v1/users/me/favorites", produces="application/json")
public class FavoriteController {

    private final FavoriteRepository favoriteRepo;
    private final TacoRepository tacoRepo;

    public FavoriteController(FavoriteRepository favoriteRepo, TacoRepository tacoRepo) {
        this.favoriteRepo = favoriteRepo;
        this.tacoRepo = tacoRepo;
    }

    @PutMapping("/{tacoId}")
    public Mono<ResponseEntity<Void>> addFavorite(
            @PathVariable("tacoId") String tacoId,
            @AuthenticationPrincipal User user) {
        
        return tacoRepo.findById(tacoId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Taco not found")))
            .flatMap(taco -> {
                Favorite fav = new Favorite(null, user.getId(), tacoId, java.time.Instant.now());
                return favoriteRepo.save(fav)
                    .map(saved -> ResponseEntity.ok().<Void>build())
                    .onErrorResume(DuplicateKeyException.class, e -> Mono.just(ResponseEntity.ok().build()));
            })
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{tacoId}")
    public Mono<ResponseEntity<Void>> removeFavorite(
            @PathVariable("tacoId") String tacoId,
            @AuthenticationPrincipal User user) {
        return favoriteRepo.deleteByUserIdAndTacoId(user.getId(), tacoId)
            .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @GetMapping
    public Flux<Favorite> getFavorites(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(page, size);
        return favoriteRepo.findByUserId(user.getId(), pageRequest);
    }
}
