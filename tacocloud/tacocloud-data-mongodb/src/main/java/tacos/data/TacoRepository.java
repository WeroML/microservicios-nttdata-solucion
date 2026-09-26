package tacos.data;

import org.springframework.data.repository.reactive.ReactiveSortingRepository;

import tacos.Taco;

public interface TacoRepository 
         extends ReactiveSortingRepository<Taco, String>, TacoSearchRepository {

}
