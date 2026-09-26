package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.api.dto.KitchenOrderResponse;

@RestController
@RequestMapping(path={"/api/v1/kitchen", "/api/kitchen"}, produces="application/json")
@PreAuthorize("hasRole('KITCHEN')")
public class KitchenApiController {

    private final KitchenService kitchenService;

    public KitchenApiController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/queue")
    public Flux<KitchenOrderResponse> getQueue() {
        return kitchenService.queue();
    }

    // 200 con la orden reclamada; 204 si la cola está vacía.
    @PostMapping("/orders/claim")
    public Mono<ResponseEntity<KitchenOrderResponse>> claimNextOrder(Authentication authentication) {
        return kitchenService.claimNext(Actor.from(authentication))
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.noContent().build())
            .contextWrite(CorrelationIdFilter.reactorContext());
    }
}
