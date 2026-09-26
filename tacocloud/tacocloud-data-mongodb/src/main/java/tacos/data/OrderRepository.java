package tacos.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import tacos.TacoOrder;

public interface OrderRepository 
         extends ReactiveCrudRepository<TacoOrder, String> {

  // Usado por el módulo web legado; la API usa OrderManagementService.
  Flux<TacoOrder> findByUserIdOrderByPlacedAtDesc(String userId, Pageable pageable);

}
