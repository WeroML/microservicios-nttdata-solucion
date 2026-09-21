package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.User;
import tacos.data.OrderRepository;
import tacos.api.dto.OrderResponse;
import tacos.api.dto.OrderMapper;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping(path="/api/v1/users/me/orders", produces="application/json")
public class UserOrdersController {

    private final OrderRepository orderRepo;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final tacos.inventory.InventoryService inventoryService;
    private final tacos.messaging.contract.OrderMessagingService orderMessages;
    private final OrderStateService orderStateService;
    private final TransactionalOrderService transactionalOrderService;
    private final TacoMetricsService metricsService;

    public UserOrdersController(OrderRepository orderRepo, OrderMapper orderMapper,
                                OrderService orderService,
                                tacos.inventory.InventoryService inventoryService,
                                tacos.messaging.contract.OrderMessagingService orderMessages,
                                OrderStateService orderStateService,
                                TransactionalOrderService transactionalOrderService,
                                TacoMetricsService metricsService) {
        this.orderRepo = orderRepo;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.orderMessages = orderMessages;
        this.orderStateService = orderStateService;
        this.transactionalOrderService = transactionalOrderService;
        this.metricsService = metricsService;
    }

    @GetMapping
    public Flux<OrderResponse> getMyOrders(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size);
        return orderRepo.findByUserOrderByPlacedAtDesc(user, pageRequest)
            .map(orderMapper::toResponse);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<OrderResponse>> getMyOrderDetails(
            @PathVariable("id") String orderId,
            @AuthenticationPrincipal User user) {
        
        return orderRepo.findById(orderId)
            .filter(order -> order.getUser() != null && order.getUser().getId().equals(user.getId()))
            .map(order -> ResponseEntity.ok(orderMapper.toResponse(order)))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reorder")
    public Mono<ResponseEntity<?>> reorder(
            @PathVariable("id") String orderId,
            @RequestBody tacos.api.dto.ReorderRequest request,
            @AuthenticationPrincipal User user) {
        
        return orderRepo.findById(orderId)
            .filter(order -> order.getUser() != null && order.getUser().getId().equals(user.getId()))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found or unauthorized")))
            .flatMap(oldOrder -> {
                tacos.api.dto.OrderCreateRequest newReq = new tacos.api.dto.OrderCreateRequest();
                newReq.setDeliveryName(oldOrder.getDeliveryName());
                newReq.setDeliveryStreet(oldOrder.getDeliveryStreet());
                newReq.setDeliveryCity(oldOrder.getDeliveryCity());
                newReq.setDeliveryState(oldOrder.getDeliveryState());
                newReq.setDeliveryZip(oldOrder.getDeliveryZip());
                newReq.setPaymentMethodId(request.getPaymentMethodId() != null ? request.getPaymentMethodId() : oldOrder.getPaymentMethodId());
                newReq.setDiscountCode(oldOrder.getDiscountCode());
                
                java.util.List<tacos.api.dto.OrderCreateRequest.OrderItemRequest> items = new java.util.ArrayList<>();
                if (oldOrder.getItems() != null) {
                    for (tacos.OrderItem item : oldOrder.getItems()) {
                        tacos.api.dto.OrderCreateRequest.OrderItemRequest ir = new tacos.api.dto.OrderCreateRequest.OrderItemRequest();
                        
                        tacos.api.dto.OrderCreateRequest.TacoRequest tr = new tacos.api.dto.OrderCreateRequest.TacoRequest();
                        tr.setName(item.getTaco().getName());
                        java.util.List<String> ingredientIds = new java.util.ArrayList<>();
                        if (item.getTaco().getIngredients() != null) {
                            for (tacos.Ingredient i : item.getTaco().getIngredients()) {
                                ingredientIds.add(i.getId());
                            }
                        }
                        tr.setIngredients(ingredientIds);
                        
                        ir.setTaco(tr);
                        ir.setQuantity(item.getQuantity());
                        items.add(ir);
                    }
                }
                newReq.setItems(items);

                return orderService.createOrderFromRequest(newReq)
                    .flatMap(newOrder -> {
                        boolean priceChanged = oldOrder.getTotal() != null && newOrder.getTotal() != null && oldOrder.getTotal().compareTo(newOrder.getTotal()) != 0;
                        if (priceChanged && !request.isConfirmPriceChange()) {
                            // Return quote/conflict
                            return Mono.just(ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(orderMapper.toResponse(newOrder)));
                        }
                        
                        newOrder.setUser(user);
                        
                        java.util.Map<String, Integer> reservations = new java.util.HashMap<>();
                        if (newOrder.getItems() != null) {
                            for (tacos.OrderItem item : newOrder.getItems()) {
                                if (item.getTaco() != null && item.getTaco().getIngredients() != null) {
                                    for (tacos.Ingredient i : item.getTaco().getIngredients()) {
                                        reservations.put(i.getId(), reservations.getOrDefault(i.getId(), 0) + item.getQuantity());
                                    }
                                }
                            }
                        }
                        orderStateService.initializeHistory(newOrder);
                        return inventoryService.reserveStock(reservations)
                            .then(transactionalOrderService.saveOrderAndOutboxEvent(newOrder))
                            .onErrorResume(e -> {
                                metricsService.recordStockRejected();
                                return inventoryService.releaseStock(reservations).then(Mono.error(e));
                            })
                            .map(saved -> (ResponseEntity<?>) ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(orderMapper.toResponse(saved)));
                    });
            })
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just((ResponseEntity<?>) ResponseEntity.notFound().build()));
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<OrderResponse>> cancelOrder(
            @PathVariable("id") String orderId,
            @AuthenticationPrincipal User user,
            @RequestParam(required=false, defaultValue="User requested cancellation") String reason) {
        
        return orderRepo.findById(orderId)
            .filter(order -> order.getUser() != null && order.getUser().getId().equals(user.getId()))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Order not found or unauthorized")))
            .flatMap(order -> orderStateService.transition(order, tacos.OrderStatus.CANCELLED, reason))
            .flatMap(order -> transactionalOrderService.saveOrderAndStatusEvent(order, tacos.messaging.contract.OrderEventType.CANCELLED))
            .map(orderMapper::toResponse)
            .map(ResponseEntity::ok)
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just(ResponseEntity.notFound().build()))
            .onErrorResume(IllegalStateException.class, e -> Mono.just(ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build()));
    }
}
