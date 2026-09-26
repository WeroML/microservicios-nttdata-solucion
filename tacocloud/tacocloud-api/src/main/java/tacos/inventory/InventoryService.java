package tacos.inventory;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.OrderItem;
import tacos.StockReservation;
import tacos.TacoOrder;
import tacos.api.error.BusinessRuleException;
import tacos.api.error.ConflictException;
import tacos.api.error.NotFoundException;

/**
 * TC-13/TC-16: operaciones de inventario atómicas sobre Mongo.
 *
 * - Cada descuento es un único update condicionado a stockOnHand >= cantidad,
 *   así dos pedidos concurrentes nunca venden más de lo que hay ni dejan stock negativo.
 * - Los ingredientes se procesan en orden alfabético de ID (orden estable).
 * - Si falta stock a mitad de la reserva, se devuelve exactamente lo ya descontado
 *   (compensación) porque no hay transacción que abarque varios ingredientes.
 * - El documento StockReservation (id = llave de reserva, vinculado a orderId)
 *   evita descontar dos veces en un reintento y liberar dos veces al cancelar.
 */
@Service
public class InventoryService {

    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    private final ReactiveMongoTemplate mongo;
    private final Clock clock;

    public InventoryService(ReactiveMongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    // Cantidad total por ingrediente de una orden, en orden estable.
    public static Map<String, Integer> quantitiesOf(TacoOrder order) {
        Map<String, Integer> quantities = new TreeMap<>();
        for (OrderItem item : order.getItems()) {
            for (Ingredient ingredient : item.getTaco().getIngredients()) {
                quantities.merge(ingredient.getId(), item.getQuantity(), Integer::sum);
            }
        }
        return quantities;
    }

    public Mono<StockReservation> reserve(String reservationId, String orderId, Map<String, Integer> quantities) {
        StockReservation reservation = new StockReservation();
        reservation.setId(reservationId);
        reservation.setOrderId(orderId);
        reservation.setQuantities(new TreeMap<>(quantities));
        reservation.setStatus(StockReservation.Status.RESERVING);
        reservation.setCreatedAt(now());
        reservation.setUpdatedAt(now());

        return mongo.insert(reservation)
            .flatMap(this::decrementAll)
            // Reintento con la misma llave: la reserva ya existe y no se descuenta de nuevo.
            .onErrorResume(DuplicateKeyException.class,
                e -> mongo.findById(reservationId, StockReservation.class));
    }

    private Mono<StockReservation> decrementAll(StockReservation reservation) {
        List<String> reserved = new ArrayList<>();
        return Flux.fromIterable(reservation.getQuantities().entrySet())
            .concatMap(entry -> decrement(entry.getKey(), entry.getValue())
                .doOnSuccess(v -> reserved.add(entry.getKey())))
            .then(markReservation(reservation.getId(), StockReservation.Status.RESERVED))
            .onErrorResume(error -> compensate(reservation, reserved)
                .then(markReservation(reservation.getId(), StockReservation.Status.FAILED))
                .then(Mono.error(error)));
    }

    private Mono<Void> decrement(String ingredientId, int amount) {
        Query query = Query.query(where("_id").is(ingredientId)
            .and("available").is(true)
            .and("stockOnHand").gte(amount));
        Update update = new Update().inc("stockOnHand", -amount).inc("version", 1);
        return mongo.updateFirst(query, update, Ingredient.class)
            .flatMap(result -> result.getModifiedCount() == 1
                ? Mono.<Void>empty()
                : Mono.error(new ConflictException(INSUFFICIENT_STOCK,
                    "Insufficient stock for ingredient " + ingredientId)));
    }

    private Mono<Void> compensate(StockReservation reservation, List<String> reserved) {
        return Flux.fromIterable(reserved)
            .concatMap(id -> increment(id, reservation.getQuantities().get(id)))
            .then();
    }

    /**
     * Libera las reservas activas de la orden exactamente una vez: sólo quien logra
     * cambiar el estado RESERVED -> RELEASED devuelve el stock.
     */
    public Mono<Boolean> release(String orderId) {
        return releaseMatching(where("orderId").is(orderId));
    }

    // PUT de orden: libera las reservas anteriores y conserva la nueva.
    public Mono<Boolean> releaseAllExcept(String orderId, String keepReservationId) {
        return releaseMatching(where("orderId").is(orderId).and("_id").ne(keepReservationId));
    }

    public Mono<Boolean> releaseReservation(String reservationId) {
        return releaseMatching(where("_id").is(reservationId));
    }

    private Mono<Boolean> releaseMatching(Criteria criteria) {
        Query query = Query.query(criteria.and("status").is(StockReservation.Status.RESERVED));
        return Mono.defer(() -> claimForRelease(query))
            .expand(released -> claimForRelease(query))
            .concatMap(reservation -> Flux.fromIterable(reservation.getQuantities().entrySet())
                .concatMap(entry -> increment(entry.getKey(), entry.getValue()))
                .then(Mono.just(reservation)))
            .hasElements();
    }

    private Mono<StockReservation> claimForRelease(Query query) {
        Update update = new Update()
            .set("status", StockReservation.Status.RELEASED)
            .set("updatedAt", now());
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false),
            StockReservation.class);
    }

    private Mono<Void> increment(String ingredientId, int amount) {
        Query query = Query.query(where("_id").is(ingredientId));
        Update update = new Update().inc("stockOnHand", amount).inc("version", 1);
        return mongo.updateFirst(query, update, Ingredient.class).then();
    }

    private Mono<StockReservation> markReservation(String id, StockReservation.Status status) {
        Query query = Query.query(where("_id").is(id));
        Update update = new Update().set("status", status).set("updatedAt", now());
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
            StockReservation.class);
    }

    /**
     * TC-13: ajuste explícito de stock por ADMIN. Operación atómica: si el ajuste
     * dejaría el stock negativo, el filtro no coincide y se rechaza con 422.
     */
    public Mono<Ingredient> adjustStock(String ingredientId, int amount) {
        Query query = Query.query(where("_id").is(ingredientId));
        if (amount < 0) {
            query.addCriteria(where("stockOnHand").gte(-amount));
        }
        Update update = new Update().inc("stockOnHand", amount).inc("version", 1);
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                Ingredient.class)
            .switchIfEmpty(Mono.defer(() -> mongo.exists(Query.query(where("_id").is(ingredientId)), Ingredient.class)
                .flatMap(exists -> Mono.error(exists
                    ? new BusinessRuleException("NEGATIVE_STOCK", "The adjustment would leave negative stock.")
                    : new NotFoundException("INGREDIENT_NOT_FOUND", "Ingredient " + ingredientId + " not found.")))));
    }

    /**
     * TC-13: consistencia entre available y stock. Pausar la venta con stock
     * (available=false, stockOnHand>0) es válido; ofrecer algo sin stock no lo es.
     */
    public static void checkCatalogConsistency(Ingredient ingredient) {
        if (ingredient.isAvailable() && ingredient.getStockOnHand() <= 0) {
            throw new BusinessRuleException("AVAILABLE_WITHOUT_STOCK",
                "An ingredient cannot be available with no stock on hand.");
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
