package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoRating;
import tacos.User;
import tacos.data.TacoRatingRepository;
import tacos.data.TacoRepository;

@RestController
@RequestMapping(path="/api/v1/tacos", produces="application/json")
public class TacoRatingController {

    private final TacoRatingRepository ratingRepo;
    private final TacoRepository tacoRepo;

    public TacoRatingController(TacoRatingRepository ratingRepo, TacoRepository tacoRepo) {
        this.ratingRepo = ratingRepo;
        this.tacoRepo = tacoRepo;
    }

    @PutMapping("/{id}/rating")
    public Mono<ResponseEntity<Void>> rateTaco(
            @PathVariable("id") String tacoId,
            @RequestBody RatingRequest request,
            @AuthenticationPrincipal User user) {
        
        if (request.getScore() < 1 || request.getScore() > 5) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return tacoRepo.findById(tacoId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Taco not found")))
            .flatMap(taco -> {
                return ratingRepo.findByUserIdAndTacoId(user.getId(), tacoId)
                    .defaultIfEmpty(new TacoRating(null, user.getId(), tacoId, 0, java.time.Instant.now()))
                    .flatMap(rating -> {
                        int oldScore = rating.getScore();
                        rating.setScore(request.getScore());
                        
                        // Update taco stats
                        if (oldScore == 0) { // new rating
                            double total = taco.getRatingAverage() * taco.getRatingCount() + request.getScore();
                            taco.setRatingCount(taco.getRatingCount() + 1);
                            taco.setRatingAverage(total / taco.getRatingCount());
                        } else { // update rating
                            double total = taco.getRatingAverage() * taco.getRatingCount() - oldScore + request.getScore();
                            taco.setRatingAverage(total / taco.getRatingCount());
                        }
                        
                        return tacoRepo.save(taco)
                            .then(ratingRepo.save(rating))
                            .thenReturn(ResponseEntity.ok().<Void>build());
                    });
            })
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/top")
    public Flux<Taco> getTopTacos(
            @RequestParam(defaultValue="10") int limit,
            @RequestParam(defaultValue="5") int minVotes) {
        // Find tacos where ratingCount >= minVotes, sort by ratingAverage desc, ratingCount desc, limit
        org.springframework.data.domain.PageRequest page = org.springframework.data.domain.PageRequest.of(0, limit, 
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "ratingAverage", "ratingCount"));
        
        // Let's use a custom search method or just an ad-hoc query through MongoTemplate.
        // We will need a method in TacoRepository.
        return tacoRepo.findTopByRatingCountGreaterThanEqual(minVotes, page);
    }
}

class RatingRequest {
    private int score;
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
