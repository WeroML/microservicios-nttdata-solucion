package tacos.web.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.OrderStatus;
import tacos.api.dto.OrderSummaryResponse;
import tacos.api.dto.PageResponse;

// TC-23: consulta global explícita para ADMIN, con filtros; no es un bypass de las rutas "me".
@RestController
@RequestMapping(path={"/api/v1/admin/orders", "/api/admin/orders"}, produces="application/json")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrdersController {

    private final OrderManagementService managementService;

    public AdminOrdersController(OrderManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping
    public Mono<PageResponse<OrderSummaryResponse>> searchOrders(
            @RequestParam(required=false) OrderStatus status,
            @RequestParam(required=false) String userId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return managementService.adminSearch(status, userId, page, size);
    }
}
