package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.FavoriteResponse;
import tacos.api.dto.PageResponse;

// TC-21: rutas "me"; el usuario nunca viaja en el path ni en el body.
@RestController
@RequestMapping(path={"/api/v1/users/me/favorites", "/api/users/me/favorites"}, produces="application/json")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PutMapping("/{tacoId}")
    public Mono<ResponseEntity<Void>> addFavorite(@PathVariable("tacoId") String tacoId,
                                                  Authentication authentication) {
        return favoriteService.add(Actor.from(authentication).getUserId(), tacoId)
            .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @DeleteMapping("/{tacoId}")
    public Mono<ResponseEntity<Void>> removeFavorite(@PathVariable("tacoId") String tacoId,
                                                     Authentication authentication) {
        return favoriteService.remove(Actor.from(authentication).getUserId(), tacoId)
            .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @GetMapping
    public Mono<PageResponse<FavoriteResponse>> getFavorites(Authentication authentication,
                                                             @RequestParam(defaultValue="0") int page,
                                                             @RequestParam(defaultValue="20") int size) {
        return favoriteService.list(Actor.from(authentication).getUserId(), page, size);
    }
}
