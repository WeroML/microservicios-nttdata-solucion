package tacos.web.api;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.error.BadRequestException;
import tacos.api.error.ConflictException;

/**
 * TC-34: Idempotency-Key en creación de órdenes.
 *
 * - Misma llave + misma petición: se devuelve la misma orden (200) sin crear otra.
 * - Misma llave + petición distinta: 409 IDEMPOTENCY_KEY_REUSED.
 * - Petición concurrente con la misma llave: el índice único deja pasar sólo una;
 *   la otra recibe 409 REQUEST_IN_PROGRESS.
 * - Si la creación falla, el registro queda FAILED y la misma llave puede reintentarse.
 *   Un IN_PROGRESS abandonado se puede retomar después de inProgressTimeout.
 * - El registro se marca COMPLETED dentro de la transacción de orden + outbox.
 * - Retención con TTL (tacocloud.idempotency.retention).
 */
@Service
public class IdempotencyService {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final char SEPARATOR = '\u001F';

    private final IdempotencyRecordRepository repository;
    private final ReactiveMongoTemplate mongo;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ReactiveMongoTemplate mongo,
                              IdempotencyProperties properties, Clock clock) {
        this.repository = repository;
        this.mongo = mongo;
        this.properties = properties;
        this.clock = clock;
    }

    public static void validateKey(String key) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must be 8 to 64 characters: letters, digits, '-' or '_'.");
        }
    }

    // Representación canónica de los campos que definen la orden (no el JSON textual).
    public String hash(OrderCreateRequest request) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, request.getDeliveryName());
        append(canonical, request.getDeliveryStreet());
        append(canonical, request.getDeliveryCity());
        append(canonical, request.getDeliveryState());
        append(canonical, request.getDeliveryZip());
        append(canonical, request.getPaymentMethodId());
        append(canonical, tacos.discount.DiscountService.normalize(request.getDiscountCode()));
        for (OrderCreateRequest.OrderItemRequest item : request.getItems()) {
            append(canonical, item.getTaco().getName());
            append(canonical, String.join(",", item.getTaco().getIngredientIds()));
            append(canonical, String.valueOf(item.getQuantity()));
        }
        return sha256(canonical.toString());
    }

    public String hash(String canonical) {
        return sha256(canonical);
    }

    /**
     * Intenta reservar la llave. Emite vacío si esta petición debe procesarse,
     * o el orderId existente si es una repetición ya completada.
     */
    public Mono<String> begin(String userId, String key, String requestHash) {
        Instant now = Instant.now(clock);
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyRecord.Status.IN_PROGRESS);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setExpiresAt(now.plus(properties.getRetention()));

        return repository.save(record)
            .then(Mono.<String>empty())
            .onErrorResume(DuplicateKeyException.class, e ->
                repository.findByUserIdAndIdempotencyKey(userId, key)
                    .flatMap(existing -> resolveExisting(existing, requestHash)));
    }

    private Mono<String> resolveExisting(IdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            return Mono.error(new ConflictException("IDEMPOTENCY_KEY_REUSED",
                "The Idempotency-Key was already used with a different request."));
        }
        if (existing.getStatus() == IdempotencyRecord.Status.COMPLETED) {
            return Mono.just(existing.getOrderId());
        }
        return takeOver(existing)
            .flatMap(taken -> taken
                ? Mono.<String>empty()
                : Mono.error(new ConflictException("REQUEST_IN_PROGRESS",
                    "A request with this Idempotency-Key is still being processed.")));
    }

    // Retoma un registro FAILED o un IN_PROGRESS abandonado; sólo un proceso lo logra.
    private Mono<Boolean> takeOver(IdempotencyRecord existing) {
        Instant now = Instant.now(clock);
        Instant staleBefore = now.minus(properties.getInProgressTimeout());
        Query query = Query.query(where("_id").is(existing.getId()).orOperator(
            where("status").is(IdempotencyRecord.Status.FAILED),
            new Criteria().andOperator(
                where("status").is(IdempotencyRecord.Status.IN_PROGRESS),
                where("updatedAt").lt(staleBefore))));
        Update update = new Update()
            .set("status", IdempotencyRecord.Status.IN_PROGRESS)
            .set("updatedAt", now);
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                IdempotencyRecord.class)
            .map(r -> true)
            .defaultIfEmpty(false);
    }

    public Mono<Void> complete(String userId, String key, String orderId) {
        return updateStatus(userId, key, IdempotencyRecord.Status.COMPLETED, orderId);
    }

    public Mono<Void> fail(String userId, String key) {
        return updateStatus(userId, key, IdempotencyRecord.Status.FAILED, null);
    }

    private Mono<Void> updateStatus(String userId, String key, IdempotencyRecord.Status status, String orderId) {
        Query query = Query.query(where("userId").is(userId).and("idempotencyKey").is(key));
        Update update = new Update().set("status", status).set("updatedAt", Instant.now(clock));
        if (orderId != null) {
            update.set("orderId", orderId);
        }
        return mongo.updateFirst(query, update, IdempotencyRecord.class).then();
    }

    private static void append(StringBuilder sb, String value) {
        sb.append(value == null ? "" : value.trim()).append(SEPARATOR);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Llave de idempotencia de una operación concreta (usuario + key).
    public static final class Key {
        private final String userId;
        private final String value;

        public Key(String userId, String value) {
            this.userId = userId;
            this.value = value;
        }

        public String getUserId() {
            return userId;
        }

        public String getValue() {
            return value;
        }
    }
}
