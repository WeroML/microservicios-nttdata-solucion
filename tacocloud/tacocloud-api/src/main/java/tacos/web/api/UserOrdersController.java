package tacos.web.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.OrderMapper;
import tacos.api.dto.OrderResponse;
import tacos.api.dto.OrderSummaryResponse;
import tacos.api.dto.PageResponse;

// TC-23: historial privado. El usuario sale de la autenticación, nunca de la URL.
@RestController
@RequestMapping(path={"/api/v1/users/me/orders", "/api/users/me/orders"}, produces="application/json")
public class UserOrdersController {

    private final OrderManagementService managementService;
    private final OrderMapper orderMapper;

    public UserOrdersController(OrderManagementService managementService, OrderMapper orderMapper) {
        this.managementService = managementService;
        this.orderMapper = orderMapper;
    }

    // Página fuera de rango: 200 con content vacío y hasNext=false.
    @GetMapping
    public Mono<PageResponse<OrderSummaryResponse>> getMyOrders(
            Authentication authentication,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return managementService.listMine(Actor.from(authentication), page, size);
    }

    // Una orden ajena responde 404: no se revela que existe.
    @GetMapping("/{id}")
    public Mono<OrderResponse> getMyOrderDetails(@PathVariable("id") String orderId,
                                                 Authentication authentication) {
        return managementService.findMine(orderId, Actor.from(authentication))
            .map(orderMapper::toResponse);
    }
}
