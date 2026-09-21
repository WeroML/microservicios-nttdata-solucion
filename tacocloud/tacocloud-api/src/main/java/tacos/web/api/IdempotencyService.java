package tacos.web.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Collectors;
import tacos.api.dto.OrderCreateRequest;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    public String hashRequest(OrderCreateRequest request) {
        try {
            // Canonical representation: items and discount code
            String items = request.getItems() == null ? "" : request.getItems().stream()
                .map(item -> item.getTaco().getName() + ":" + item.getTaco().getIngredients().toString() + ":" + item.getQuantity())
                .sorted()
                .collect(Collectors.joining(","));
            String canonical = "items=[" + items + "];discount=" + request.getDiscountCode();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not found", e);
        }
    }

    // Try to claim key. Returns empty if claimed successfully. Returns existing record if duplicate.
    public Mono<IdempotencyRecord> tryClaim(String userId, String idempotencyKey, String hash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(hash);
        record.setStatus("IN_PROGRESS");
        record.setCreatedAt(Instant.now());

        return repository.save(record)
            .flatMap(r -> Mono.<IdempotencyRecord>empty())
            .onErrorResume(DuplicateKeyException.class, e -> repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey));
    }

    public Mono<Void> complete(String userId, String idempotencyKey, String orderId) {
        return repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .flatMap(record -> {
                record.setStatus("COMPLETED");
                record.setOrderId(orderId);
                return repository.save(record);
            })
            .then();
    }
}
