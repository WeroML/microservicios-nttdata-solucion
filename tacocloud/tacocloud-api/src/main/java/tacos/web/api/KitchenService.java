package tacos.web.api;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.OrderItem;
import tacos.OrderStatus;
import tacos.OrderStatusHistory;
import tacos.TacoOrder;
import tacos.api.dto.KitchenOrderResponse;
import tacos.api.error.ConflictException;
import tacos.messaging.contract.OrderEventType;

/**
 * TC-26: cola de cocina y claim atómico.
 * - La cola son las órdenes CREATED ordenadas por placedAt y luego ID (FIFO estable).
 * - El claim es un findAndModify condicionado a status=CREATED: si dos estaciones
 *   compiten, cada una obtiene una orden distinta o la cola vacía.
 * - Una estación (cocinero) no puede reclamar otra orden mientras tenga una ACCEPTED
 *   sin empezar.
 */
@Service
public class KitchenService {

    public static final String ORIGIN = "KITCHEN";
    private static final Sort FIFO = Sort.by(Sort.Order.asc("placedAt"), Sort.Order.asc("_id"));

    private final ReactiveMongoTemplate mongo;
    private final TransactionalOrderService transactionalOrderService;
    private final KitchenProperties properties;
    private final TacoMetricsService metrics;
    private final Clock clock;

    public KitchenService(ReactiveMongoTemplate mongo, TransactionalOrderService transactionalOrderService,
                          KitchenProperties properties, TacoMetricsService metrics, Clock clock) {
        this.mongo = mongo;
        this.transactionalOrderService = transactionalOrderService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public Flux<KitchenOrderResponse> queue() {
        return inProgressCount().flatMapMany(inProgress ->
            mongo.find(Query.query(where("status").is(OrderStatus.CREATED)).with(FIFO), TacoOrder.class)
                .index()
                .map(indexed -> toResponse(indexed.getT2(),
                    estimate(indexed.getT2(), inProgress + indexed.getT1()))));
    }

    @Transactional
    public Mono<KitchenOrderResponse> claimNext(Actor cook) {
        return mongo.exists(Query.query(where("status").is(OrderStatus.ACCEPTED).and("cookId").is(cook.getUsername())),
                TacoOrder.class)
            .flatMap(busy -> busy
                ? Mono.error(new ConflictException("STATION_BUSY",
                    "Finish starting your accepted order before claiming another one."))
                : claim(cook));
    }

    private Mono<KitchenOrderResponse> claim(Actor cook) {
        Instant now = Instant.now(clock);
        Update update = new Update()
            .set("status", OrderStatus.ACCEPTED)
            .set("stationId", properties.getStationId())
            .set("cookId", cook.getUsername())
            .push("statusHistory", new OrderStatusHistory(OrderStatus.ACCEPTED, now, cook.getUsername(),
                ORIGIN, "Claimed by " + properties.getStationId()))
            .inc("version", 1);
        Query nextCreated = Query.query(where("status").is(OrderStatus.CREATED)).with(FIFO);

        return mongo.findAndModify(nextCreated, update, FindAndModifyOptions.options().returnNew(true), TacoOrder.class)
            .flatMap(claimed -> inProgressCount()
                .flatMap(inProgress -> setEta(claimed, estimate(claimed, inProgress - 1))))
            .flatMap(claimed -> transactionalOrderService.registerEvent(claimed, OrderEventType.STATUS_CHANGED)
                .thenReturn(claimed))
            .doOnNext(claimed -> metrics.recordKitchenClaimLatency(
                Duration.between(claimed.getPlacedAt().toInstant(), now)))
            .map(claimed -> toResponse(claimed, claimed.getEstimatedPrepMinutes()));
    }

    private Mono<TacoOrder> setEta(TacoOrder order, int minutes) {
        return mongo.findAndModify(Query.query(where("_id").is(order.getId())),
            new Update().set("estimatedPrepMinutes", minutes),
            FindAndModifyOptions.options().returnNew(true), TacoOrder.class);
    }

    private Mono<Long> inProgressCount() {
        return mongo.count(Query.query(where("status").in(OrderStatus.ACCEPTED, OrderStatus.PREPARING)),
            TacoOrder.class);
    }

    public int estimate(TacoOrder order, long ordersAhead) {
        KitchenProperties.Eta eta = properties.getEta();
        int units = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        long distinctIngredients = order.getItems().stream()
            .flatMap(item -> item.getTaco().getIngredients().stream())
            .map(Ingredient::getId)
            .distinct()
            .count();
        return (int) (eta.getBaseMinutes()
            + (long) eta.getPerTacoMinutes() * units
            + eta.getPerIngredientMinutes() * distinctIngredients
            + eta.getPerOrderAheadMinutes() * Math.max(0, ordersAhead));
    }

    static KitchenOrderResponse toResponse(TacoOrder order, Integer eta) {
        KitchenOrderResponse resp = new KitchenOrderResponse();
        resp.setId(order.getId());
        resp.setPlacedAt(order.getPlacedAt());
        resp.setStatus(order.getStatus().name());
        resp.setStationId(order.getStationId());
        resp.setCookId(order.getCookId());
        resp.setEstimatedPrepMinutes(eta);
        List<KitchenOrderResponse.Item> items = order.getItems().stream().map(item -> {
            KitchenOrderResponse.Item i = new KitchenOrderResponse.Item();
            i.setTacoName(item.getTaco().getName());
            i.setIngredients(item.getTaco().getIngredients().stream().map(Ingredient::getName)
                .collect(Collectors.toList()));
            i.setQuantity(item.getQuantity());
            return i;
        }).collect(Collectors.toList());
        resp.setItems(items);
        return resp;
    }
}
