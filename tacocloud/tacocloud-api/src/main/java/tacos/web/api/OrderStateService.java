package tacos.web.api;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import tacos.OrderStatus;
import tacos.OrderStatusHistory;
import tacos.TacoOrder;
import tacos.User;
import tacos.api.error.ConflictException;
import tacos.api.error.ForbiddenException;

/**
 * TC-25: máquina de estados central de la orden. Ningún controlador ni
 * listener valida transiciones por su cuenta.
 *
 * Transición                        Quién puede ejecutarla
 * CREATED -> ACCEPTED               KITCHEN, ADMIN
 * ACCEPTED -> PREPARING             KITCHEN, ADMIN
 * PREPARING -> READY                KITCHEN, ADMIN
 * READY -> OUT_FOR_DELIVERY         KITCHEN, ADMIN
 * OUT_FOR_DELIVERY -> DELIVERED     KITCHEN, ADMIN
 * CREATED -> CANCELLED              dueño de la orden, ADMIN
 * ACCEPTED -> CANCELLED             dueño de la orden, ADMIN
 *
 * El usuario puede cancelar sólo antes de PREPARING. Repetir la transición al
 * estado actual es idempotente (no agrega historial). Cualquier otra transición
 * responde 409. La concurrencia la detecta @Version al guardar (409).
 */
@Service
public class OrderStateService {

    public static final String OWNER = "OWNER";

    private static final Map<OrderStatus, Map<OrderStatus, Set<String>>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        Set<String> kitchen = Set.of(User.ROLE_KITCHEN, User.ROLE_ADMIN);
        Set<String> owner = Set.of(OWNER, User.ROLE_ADMIN);
        allow(OrderStatus.CREATED, OrderStatus.ACCEPTED, kitchen);
        allow(OrderStatus.ACCEPTED, OrderStatus.PREPARING, kitchen);
        allow(OrderStatus.PREPARING, OrderStatus.READY, kitchen);
        allow(OrderStatus.READY, OrderStatus.OUT_FOR_DELIVERY, kitchen);
        allow(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, kitchen);
        allow(OrderStatus.CREATED, OrderStatus.CANCELLED, owner);
        allow(OrderStatus.ACCEPTED, OrderStatus.CANCELLED, owner);
    }

    private static void allow(OrderStatus from, OrderStatus to, Set<String> roles) {
        TRANSITIONS.computeIfAbsent(from, k -> new HashMap<>()).put(to, roles);
    }

    private final Clock clock;

    public OrderStateService(Clock clock) {
        this.clock = clock;
    }

    public static boolean isAllowed(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.getOrDefault(from, Collections.emptyMap()).containsKey(to);
    }

    /**
     * Aplica la transición sobre la orden (sin guardar).
     * @return true si cambió el estado; false si ya estaba en ese estado.
     */
    public boolean transition(TacoOrder order, OrderStatus target, Actor actor, String origin, String reason) {
        if (order.getStatus() == target) {
            return false;
        }
        Set<String> allowedRoles = TRANSITIONS.getOrDefault(order.getStatus(), Collections.emptyMap()).get(target);
        if (allowedRoles == null) {
            throw new ConflictException("INVALID_TRANSITION",
                "Cannot change order from " + order.getStatus() + " to " + target + ".");
        }
        if (!canExecute(allowedRoles, order, actor)) {
            throw new ForbiddenException("TRANSITION_NOT_ALLOWED",
                "You are not allowed to change the order to " + target + ".");
        }
        order.setStatus(target);
        order.getStatusHistory().add(new OrderStatusHistory(target, Instant.now(clock),
            actor.getUsername(), origin, sanitize(reason)));
        return true;
    }

    public void initialize(TacoOrder order, String changedBy, String origin) {
        order.setStatus(OrderStatus.CREATED);
        order.getStatusHistory().clear();
        order.getStatusHistory().add(new OrderStatusHistory(OrderStatus.CREATED, Instant.now(clock),
            changedBy, origin, "Order placed"));
    }

    private static boolean canExecute(Set<String> allowedRoles, TacoOrder order, Actor actor) {
        if (allowedRoles.contains(OWNER) && actor.getUserId().equals(order.getUserId())) {
            return true;
        }
        return allowedRoles.stream().anyMatch(actor::hasRole);
    }

    // La auditoría guarda texto corto y sin caracteres de control.
    private static String sanitize(String reason) {
        if (reason == null) {
            return null;
        }
        String clean = reason.replaceAll("\\p{Cntrl}", " ").trim();
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }
}
