package tacos.data;

import org.springframework.data.domain.Sort;

import reactor.core.publisher.Flux;
import tacos.Taco;

public interface TacoSearchRepository {
    Flux<Taco> searchTacos(TacoSearchCriteria criteria, Sort sort, long offset, int limit);
}
