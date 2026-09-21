package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import tacos.Favorite;
import org.springframework.data.domain.Pageable;

public interface FavoriteRepository extends ReactiveCrudRepository<Favorite, String> {
    Mono<Favorite> findByUserIdAndTacoId(String userId, String tacoId);
    Flux<Favorite> findByUserId(String userId, Pageable pageable);
    Mono<Void> deleteByUserIdAndTacoId(String userId, String tacoId);
}
