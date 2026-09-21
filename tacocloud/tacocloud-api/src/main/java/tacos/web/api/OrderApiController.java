package tacos.web.api;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.ResponseEntity;
import tacos.TacoOrder;
import tacos.data.OrderRepository;
import tacos.messaging.contract.OrderMessagingService;

@RestController
@RequestMapping(path="/api/v1/orders",
                produces="application/json")
@CrossOrigin(origins="http://localhost:8080")
public class OrderApiController {

  private OrderRepository repo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private tacos.api.dto.OrderMapper orderMapper;
  private OrderService orderService;
  private tacos.inventory.InventoryService inventoryService;
  private tacos.web.api.OrderStateService orderStateService;
  private tacos.web.api.TransactionalOrderService transactionalOrderService;
  private tacos.web.api.TacoMetricsService metricsService;
  private tacos.web.api.IdempotencyService idempotencyService;

  public OrderApiController(OrderRepository repo,
                            OrderMessagingService orderMessages,
                            EmailOrderService emailOrderService,
                            tacos.api.dto.OrderMapper orderMapper,
                            OrderService orderService,
                            tacos.inventory.InventoryService inventoryService,
                            tacos.web.api.OrderStateService orderStateService,
                            tacos.web.api.TransactionalOrderService transactionalOrderService,
                            tacos.web.api.TacoMetricsService metricsService,
                            tacos.web.api.IdempotencyService idempotencyService) {
    this.repo = repo;
    this.orderMessages = orderMessages;
    this.emailOrderService = emailOrderService;
    this.orderMapper = orderMapper;
    this.orderService = orderService;
    this.inventoryService = inventoryService;
    this.orderStateService = orderStateService;
    this.orderStateService = orderStateService;
    this.transactionalOrderService = transactionalOrderService;
    this.metricsService = metricsService;
    this.idempotencyService = idempotencyService;
  }

  @GetMapping(produces="application/json")
  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  public Flux<tacos.api.dto.OrderResponse> allOrders() {
    return repo.findAll().map(orderMapper::toResponse);
  }

  @PostMapping(consumes="application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<org.springframework.http.ResponseEntity<tacos.api.dto.OrderResponse>> postOrder(
          @org.springframework.web.bind.annotation.RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey,
          @javax.validation.Valid @RequestBody Mono<tacos.api.dto.OrderCreateRequest> requestMono) {
      
      Mono<String> userIdMono = org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
          .map(ctx -> ctx.getAuthentication().getName())
          .defaultIfEmpty("anonymous");

      return requestMono.zipWith(userIdMono)
          .flatMap(tuple -> {
              tacos.api.dto.OrderCreateRequest request = tuple.getT1();
              String userId = tuple.getT2();
              
              if (idempotencyKey != null) {
                  String hash = idempotencyService.hashRequest(request);
                  return idempotencyService.tryClaim(userId, idempotencyKey, hash)
                      .flatMap(existingRecord -> {
                          if (!existingRecord.getRequestHash().equals(hash)) {
                              return Mono.<org.springframework.http.ResponseEntity<tacos.api.dto.OrderResponse>>error(new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used with different payload"));
                          }
                          if ("COMPLETED".equals(existingRecord.getStatus())) {
                              return repo.findById(existingRecord.getOrderId())
                                  .map(orderMapper::toResponse)
                                  .map(resp -> org.springframework.http.ResponseEntity.ok(resp)); // Return 200 OK for repeated request
                          }
                          return Mono.<org.springframework.http.ResponseEntity<tacos.api.dto.OrderResponse>>error(new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Request is currently in progress"));
                      })
                      .switchIfEmpty(Mono.defer(() -> processOrder(request, userId, idempotencyKey)));
              } else {
                  return processOrder(request, userId, null);
              }
          });
  }

  private Mono<org.springframework.http.ResponseEntity<tacos.api.dto.OrderResponse>> processOrder(tacos.api.dto.OrderCreateRequest request, String userId, String idempotencyKey) {
    return orderService.createOrderFromRequest(request)
        .flatMap(order -> {
            java.util.Map<String, Integer> reservations = new java.util.HashMap<>();
            if (order.getItems() != null) {
                for (tacos.OrderItem item : order.getItems()) {
                    if (item.getTaco() != null && item.getTaco().getIngredients() != null) {
                        for (tacos.Ingredient i : item.getTaco().getIngredients()) {
                            reservations.put(i.getId(), reservations.getOrDefault(i.getId(), 0) + item.getQuantity());
                        }
                    }
                }
            }
            orderStateService.initializeHistory(order);
            return inventoryService.reserveStock(reservations)
                .then(transactionalOrderService.saveOrderAndOutboxEvent(order))
                .onErrorResume(e -> {
                    metricsService.recordStockRejected();
                    return inventoryService.releaseStock(reservations).then(Mono.error(e));
                });
        })
        .flatMap(saved -> {
            if (idempotencyKey != null) {
                return idempotencyService.complete(userId, idempotencyKey, saved.getId()).thenReturn(saved);
            }
            return Mono.<tacos.TacoOrder>just(saved);
        })
        .map(orderMapper::toResponse)
        .map(resp -> org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(resp));
  }

  @PostMapping(path="fromEmail", consumes="application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<tacos.api.dto.OrderResponse> postOrderFromEmail(@RequestBody Mono<EmailOrder> emailOrder) {
    return emailOrderService.convertEmailOrderToDomainOrder(emailOrder)
        .flatMap(order -> {
            java.util.Map<String, Integer> reservations = new java.util.HashMap<>();
            if (order.getItems() != null) {
                for (tacos.OrderItem item : order.getItems()) {
                    if (item.getTaco() != null && item.getTaco().getIngredients() != null) {
                        for (tacos.Ingredient i : item.getTaco().getIngredients()) {
                            reservations.put(i.getId(), reservations.getOrDefault(i.getId(), 0) + item.getQuantity());
                        }
                    }
                }
            }
            orderStateService.initializeHistory(order);
            return inventoryService.reserveStock(reservations)
                .then(transactionalOrderService.saveOrderAndOutboxEvent(order))
                .onErrorResume(e -> {
                    metricsService.recordStockRejected();
                    return inventoryService.releaseStock(reservations).then(Mono.error(e));
                });
        })
        .map(orderMapper::toResponse);
  }

  @PutMapping(path="/{orderId}", consumes="application/json")
  public Mono<ResponseEntity<tacos.api.dto.OrderResponse>> putOrder(@PathVariable("orderId") String orderId, @javax.validation.Valid @RequestBody tacos.api.dto.OrderCreateRequest order) {
    return orderService.createOrderFromRequest(order)
        .flatMap(newOrder -> repo.findById(orderId)
            .flatMap(existing -> {
              newOrder.setId(existing.getId());
              newOrder.setUser(existing.getUser());
              newOrder.setPlacedAt(existing.getPlacedAt());
              newOrder.setPaymentMethodId(existing.getPaymentMethodId());
              return repo.save(newOrder);
            })
        )
        .map(saved -> org.springframework.http.ResponseEntity.ok(orderMapper.toResponse(saved)))
        .defaultIfEmpty(org.springframework.http.ResponseEntity.notFound().build());
  }

  @PatchMapping(path="/{orderId}", consumes="application/json")
  public Mono<ResponseEntity<tacos.api.dto.OrderResponse>> patchOrder(@PathVariable("orderId") String orderId,
                          @RequestBody OrderPatchRequest patch) {

    return repo.findById(orderId)
        .flatMap(order -> {
          if (patch.getDeliveryName() != null) {
            order.setDeliveryName(patch.getDeliveryName());
          }
          if (patch.getDeliveryStreet() != null) {
            order.setDeliveryStreet(patch.getDeliveryStreet());
          }
          if (patch.getDeliveryCity() != null) {
            order.setDeliveryCity(patch.getDeliveryCity());
          }
          if (patch.getDeliveryState() != null) {
            order.setDeliveryState(patch.getDeliveryState());
          }
          if (patch.getDeliveryZip() != null) {
            order.setDeliveryZip(patch.getDeliveryZip());
          }
          return repo.save(order);
        })
        .map(saved -> org.springframework.http.ResponseEntity.ok(orderMapper.toResponse(saved)))
        .defaultIfEmpty(org.springframework.http.ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{orderId}")
  public Mono<ResponseEntity<Void>> deleteOrder(@PathVariable("orderId") String orderId) {
    return repo.findById(orderId)
        .flatMap(existing -> repo.deleteById(orderId)
            .then(Mono.just(new org.springframework.http.ResponseEntity<Void>(HttpStatus.NO_CONTENT))))
        .defaultIfEmpty(org.springframework.http.ResponseEntity.notFound().build());
  }

  @PatchMapping(path="/{orderId}/status", consumes="application/json")
  @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN')")
  public Mono<ResponseEntity<tacos.api.dto.OrderResponse>> updateStatus(
          @PathVariable("orderId") String orderId,
          @RequestBody tacos.api.dto.OrderStatusUpdateRequest request) {
      
      return repo.findById(orderId)
          .flatMap(order -> orderStateService.transition(order, request.getStatus(), request.getReason()))
          .flatMap(order -> transactionalOrderService.saveOrderAndStatusEvent(order, tacos.messaging.contract.OrderEventType.STATUS_CHANGED))
          .map(saved -> org.springframework.http.ResponseEntity.ok(orderMapper.toResponse(saved)))
          .onErrorResume(IllegalStateException.class, e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build()))
          .defaultIfEmpty(ResponseEntity.notFound().build());
  }
}
