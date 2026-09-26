package tacos.web.api;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.PaymentMethod;
import tacos.TacoOrder;

/**
 * TC-12: migración que detecta y elimina datos de tarjeta heredados
 * (ccNumber, ccCVV, ccExpiration) en las colecciones del laboratorio.
 * Corre al arrancar; también existe como script en scripts/mongo/tc12-remove-card-data.js.
 */
@Component
public class LegacyPaymentDataMigration {

    private static final Logger log = LoggerFactory.getLogger(LegacyPaymentDataMigration.class);
    static final String[] LEGACY_FIELDS = {"ccNumber", "ccCVV", "ccExpiration"};

    private final ReactiveMongoTemplate mongo;

    public LegacyPaymentDataMigration(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        removeLegacyCardData().subscribe(
            count -> log.info("Legacy card data removed from {} documents", count),
            error -> log.error("Legacy card data migration failed", error));
    }

    public Mono<Long> removeLegacyCardData() {
        return Flux.just(mongo.getCollectionName(TacoOrder.class), mongo.getCollectionName(PaymentMethod.class))
            .concatMap(this::cleanCollection)
            .reduce(0L, Long::sum);
    }

    private Mono<Long> cleanCollection(String collection) {
        Criteria anyLegacyField = new Criteria().orOperator(
            Criteria.where(LEGACY_FIELDS[0]).exists(true),
            Criteria.where(LEGACY_FIELDS[1]).exists(true),
            Criteria.where(LEGACY_FIELDS[2]).exists(true));
        Update unset = new Update();
        for (String field : LEGACY_FIELDS) {
            unset.unset(field);
        }
        return mongo.updateMulti(Query.query(anyLegacyField), unset, Document.class, collection)
            .map(result -> result.getModifiedCount());
    }
}
