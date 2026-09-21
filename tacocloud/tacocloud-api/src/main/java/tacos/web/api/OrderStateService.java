package tacos.web.api;

import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import tacos.OrderStatus;
import tacos.OrderStatusHistory;
import tacos.TacoOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderStateService {

    // Allowed transitions: FROM -> list of allowed TO
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
        OrderStatus.CREATED, Set.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
        OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
        OrderStatus.PREPARING, Set.of(OrderStatus.READY), // cannot be cancelled once preparing
        OrderStatus.READY, Set.of(OrderStatus.OUT_FOR_DELIVERY),
        OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED)
    );

    public Mono<TacoOrder> transition(TacoOrder order, OrderStatus newStatus, String reason) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getName)
            .defaultIfEmpty("SYSTEM")
            .flatMap(username -> {
                if (order.getStatus() == newStatus) {
                    return Mono.just(order); // Idempotent
                }
                
                Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
                if (!allowed.contains(newStatus)) {
                    return Mono.error(new IllegalStateException(
                        String.format("Invalid transition from %s to %s", order.getStatus(), newStatus)));
                }

                // If user is cancelling, only allowed before PREPARING
                if (newStatus == OrderStatus.CANCELLED && 
                   (order.getStatus() == OrderStatus.PREPARING || order.getStatus() == OrderStatus.READY)) {
                    return Mono.error(new IllegalStateException("Order cannot be cancelled at this stage"));
                }

                order.setStatus(newStatus);
                order.getStatusHistory().add(new OrderStatusHistory(newStatus, Instant.now(), username, reason));
                
                return Mono.just(order);
            });
    }

    public void initializeHistory(TacoOrder order) {
        if (order.getStatusHistory().isEmpty()) {
            order.getStatusHistory().add(new OrderStatusHistory(OrderStatus.CREATED, Instant.now(), 
                order.getUser() != null ? order.getUser().getUsername() : "SYSTEM", "Order placed"));
        }
    }
}
