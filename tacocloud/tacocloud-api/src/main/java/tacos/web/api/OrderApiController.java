package tacos.web.api;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.dto.OrderMapper;
import tacos.api.dto.OrderPatchRequest;
import tacos.api.dto.OrderResponse;
import tacos.api.dto.OrderStatusUpdateRequest;
import tacos.api.dto.ReorderRequest;
import tacos.api.dto.ReorderResponse;

/**
 * Los controladores sólo traducen HTTP <-> casos de uso: reciben DTOs (nunca
 * TacoOrder), toman la identidad de la autenticación y devuelven la cadena
 * reactiva al framework, que es el único que suscribe.
 */
@RestController
@RequestMapping(path={"/api/v1/orders", "/api/orders"}, produces="application/json")
public class OrderApiController {

  private final OrderService orderService;
  private final OrderManagementService managementService;
  private final OrderMapper orderMapper;

  public OrderApiController(OrderService orderService, OrderManagementService managementService,
                            OrderMapper orderMapper) {
    this.orderService = orderService;
    this.managementService = managementService;
    this.orderMapper = orderMapper;
  }

  // TC-14/TC-34: 201 al crear; 200 con la misma orden si se repite la Idempotency-Key.
  @PostMapping(consumes="application/json")
  public Mono<ResponseEntity<OrderResponse>> postOrder(
          @RequestHeader(value=IdempotencyService.IDEMPOTENCY_HEADER, required=false) String idempotencyKey,
          @Valid @RequestBody OrderCreateRequest request,
          Authentication authentication) {
    return orderService.placeOrder(request, Actor.from(authentication), idempotencyKey)
        .map(placement -> ResponseEntity.status(placement.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .body(orderMapper.toResponse(placement.getOrder())))
        .contextWrite(CorrelationIdFilter.reactorContext());
  }

  // TC-15/TC-17/TC-18: cotiza con las mismas reglas que la creación, sin crear la orden.
  @PostMapping(path="/quote", consumes="application/json")
  public Mono<OrderResponse> quote(@Valid @RequestBody OrderCreateRequest request, Authentication authentication) {
    return orderService.quote(request, Actor.from(authentication))
        .map(orderMapper::toResponse);
  }

  // TC-07: 201 sólo cuando conversión, reserva, guardado y registro del evento terminaron.
  @PostMapping(path="/fromEmail", consumes="application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<OrderResponse> postOrderFromEmail(@RequestBody EmailOrder emailOrder) {
    return orderService.placeFromEmail(Mono.just(emailOrder))
        .map(orderMapper::toResponse)
        .contextWrite(CorrelationIdFilter.reactorContext());
  }

  // TC-05: el ID de la ruta decide qué orden se reemplaza; el body no trae ID.
  @PutMapping(path="/{orderId}", consumes="application/json")
  public Mono<OrderResponse> putOrder(@PathVariable("orderId") String orderId,
                                      @Valid @RequestBody OrderCreateRequest request,
                                      Authentication authentication) {
    return managementService.replace(orderId, request, Actor.from(authentication))
        .map(orderMapper::toResponse);
  }

  // TC-04
  @PatchMapping(path="/{orderId}", consumes="application/json")
  public Mono<OrderResponse> patchOrder(@PathVariable("orderId") String orderId,
                                        @Valid @RequestBody OrderPatchRequest patch,
                                        Authentication authentication) {
    return managementService.patch(orderId, patch, Actor.from(authentication))
        .map(orderMapper::toResponse);
  }

  // TC-05: DELETE cancela (no borra físicamente). 204 cuando el efecto ya terminó.
  @DeleteMapping("/{orderId}")
  public Mono<ResponseEntity<Void>> deleteOrder(@PathVariable("orderId") String orderId,
                                                Authentication authentication) {
    return managementService.cancel(orderId, Actor.from(authentication), "Deleted by client", "api")
        .thenReturn(ResponseEntity.noContent().<Void>build())
        .contextWrite(CorrelationIdFilter.reactorContext());
  }

  // TC-25: cancelación por el dueño.
  @PostMapping("/{orderId}/cancel")
  public Mono<OrderResponse> cancelOrder(@PathVariable("orderId") String orderId,
                                         @RequestParam(defaultValue="Cancelled by customer") String reason,
                                         Authentication authentication) {
    return managementService.cancel(orderId, Actor.from(authentication), reason, "api")
        .map(orderMapper::toResponse)
        .contextWrite(CorrelationIdFilter.reactorContext());
  }

  // TC-25: cambios de estado de cocina/ADMIN.
  @PatchMapping(path="/{orderId}/status", consumes="application/json")
  public Mono<OrderResponse> updateStatus(@PathVariable("orderId") String orderId,
                                          @Valid @RequestBody OrderStatusUpdateRequest request,
                                          Authentication authentication) {
    return managementService.updateStatus(orderId, request.getStatus(), request.getReason(),
            Actor.from(authentication))
        .map(orderMapper::toResponse)
        .contextWrite(CorrelationIdFilter.reactorContext());
  }

  // TC-24: 201 con la orden nueva, o 409 con la cotización y sus diferencias si falta confirmar.
  @PostMapping(path="/{orderId}/reorder", consumes="application/json")
  public Mono<ResponseEntity<ReorderResponse>> reorder(
          @PathVariable("orderId") String orderId,
          @RequestHeader(value=IdempotencyService.IDEMPOTENCY_HEADER, required=false) String idempotencyKey,
          @Valid @RequestBody ReorderRequest request,
          Authentication authentication) {
    return orderService.reorder(orderId, request, Actor.from(authentication), idempotencyKey)
        .map(result -> ResponseEntity.status(result.isConfirmed() ? HttpStatus.CREATED : HttpStatus.CONFLICT)
            .body(new ReorderResponse(result.isConfirmed(), orderMapper.toResponse(result.getOrder()),
                result.getDifferences())))
        .contextWrite(CorrelationIdFilter.reactorContext());
  }
}
