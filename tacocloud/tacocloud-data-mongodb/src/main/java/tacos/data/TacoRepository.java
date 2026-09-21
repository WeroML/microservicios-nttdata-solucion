package tacos.data;

import org.springframework.data.repository.reactive.ReactiveSortingRepository;

import tacos.Taco;


public interface TacoRepository 
         extends ReactiveSortingRepository<Taco, String>, TacoSearchRepository {
    reactor.core.publisher.Flux<Taco> findTopByRatingCountGreaterThanEqual(int minVotes, org.springframework.data.domain.Pageable pageable);
}
