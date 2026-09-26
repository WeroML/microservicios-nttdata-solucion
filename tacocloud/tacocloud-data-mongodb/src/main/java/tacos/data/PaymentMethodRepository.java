package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.PaymentMethod;

public interface PaymentMethodRepository 
         extends ReactiveCrudRepository<PaymentMethod, String> {

  Flux<PaymentMethod> findByUserId(String userId);

  Mono<PaymentMethod> findByIdAndUserId(String id, String userId);

}
