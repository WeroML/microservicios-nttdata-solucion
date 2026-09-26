package tacos.web.api;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.api.dto.RatingRequest;
import tacos.api.dto.TacoRankingResponse;

@RestController
@RequestMapping(path={"/api/v1/tacos", "/api/tacos"}, produces="application/json")
public class TacoRatingController {

    private final RatingService ratingService;

    public TacoRatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    // El voto es del usuario autenticado; score fuera de 1..5 responde 400.
    @PutMapping(path="/{id}/rating", consumes="application/json")
    public Mono<ResponseEntity<Void>> rateTaco(@PathVariable("id") String tacoId,
                                               @Valid @RequestBody RatingRequest request,
                                               Authentication authentication) {
        return ratingService.rate(tacoId, Actor.from(authentication).getUserId(), request.getScore())
            .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @GetMapping("/top")
    public Flux<TacoRankingResponse> getTopTacos(@RequestParam(defaultValue="10") int limit) {
        return ratingService.top(limit);
    }
}
