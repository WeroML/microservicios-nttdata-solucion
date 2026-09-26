package tacos.web.api;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoRating;
import tacos.api.dto.TacoRankingResponse;
import tacos.api.error.BadRequestException;
import tacos.api.error.NotFoundException;
import tacos.data.TacoRepository;

/**
 * TC-22: calificaciones y ranking.
 * - Un voto por usuario y taco: upsert atómico sobre el índice único userId+tacoId;
 *   repetir el PUT cambia el voto, no suma otro.
 * - El ranking se calcula en Mongo con una agregación (promedio, conteo y distribución),
 *   sin consultas N+1 por taco.
 * - Sólo entran tacos con al menos minVotes votos (configurable).
 * - Desempate: promedio desc, cantidad de votos desc, tacoId asc.
 * - El promedio se expone con 2 decimales, HALF_UP.
 */
@Service
public class RatingService {

    private final ReactiveMongoTemplate mongo;
    private final TacoRepository tacoRepo;
    private final RatingProperties properties;
    private final Clock clock;

    public RatingService(ReactiveMongoTemplate mongo, TacoRepository tacoRepo,
                         RatingProperties properties, Clock clock) {
        this.mongo = mongo;
        this.tacoRepo = tacoRepo;
        this.properties = properties;
        this.clock = clock;
    }

    public Mono<Void> rate(String tacoId, String userId, int score) {
        return tacoRepo.existsById(tacoId)
            .flatMap(exists -> exists
                ? upsert(tacoId, userId, score)
                    // Dos PUT simultáneos del mismo usuario: el índice deja uno; se reintenta como update.
                    .onErrorResume(DuplicateKeyException.class, e -> upsert(tacoId, userId, score))
                : Mono.error(new NotFoundException("TACO_NOT_FOUND", "Taco " + tacoId + " not found.")));
    }

    private Mono<Void> upsert(String tacoId, String userId, int score) {
        Instant now = Instant.now(clock);
        Query query = Query.query(where("userId").is(userId).and("tacoId").is(tacoId));
        Update update = new Update()
            .set("score", score)
            .set("updatedAt", now)
            .setOnInsert("createdAt", now);
        return mongo.upsert(query, update, TacoRating.class).then();
    }

    public Flux<TacoRankingResponse> top(int limit) {
        if (limit < 1 || limit > properties.getMaxLimit()) {
            return Flux.error(new BadRequestException("INVALID_LIMIT",
                "limit must be between 1 and " + properties.getMaxLimit() + "."));
        }
        Aggregation aggregation = newAggregation(
            group("tacoId")
                .avg("score").as("average")
                .count().as("votes")
                .sum(scoreIs(1)).as("s1")
                .sum(scoreIs(2)).as("s2")
                .sum(scoreIs(3)).as("s3")
                .sum(scoreIs(4)).as("s4")
                .sum(scoreIs(5)).as("s5"),
            match(where("votes").gte(properties.getMinVotes())),
            sort(Sort.by(Sort.Order.desc("average"), Sort.Order.desc("votes"), Sort.Order.asc("_id"))),
            limit(limit));

        return mongo.aggregate(aggregation, TacoRating.class, RatingSummary.class)
            .collectList()
            .flatMapMany(summaries -> tacoRepo.findAllById(summaries.stream().map(RatingSummary::getId)
                    .collect(Collectors.toList()))
                .collectMap(Taco::getId, Function.identity())
                .flatMapMany(tacos -> Flux.fromIterable(summaries)
                    .filter(s -> tacos.containsKey(s.getId()))
                    .map(s -> toResponse(s, tacos.get(s.getId())))));
    }

    private static ConditionalOperators.Cond scoreIs(int value) {
        return ConditionalOperators.when(where("score").is(value)).then(1).otherwise(0);
    }

    private static TacoRankingResponse toResponse(RatingSummary s, Taco taco) {
        TacoRankingResponse resp = new TacoRankingResponse();
        resp.setTacoId(s.getId());
        resp.setTacoName(taco.getName());
        resp.setAverage(BigDecimal.valueOf(s.getAverage()).setScale(2, RoundingMode.HALF_UP));
        resp.setVotes(s.getVotes());
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        distribution.put(1, s.getS1());
        distribution.put(2, s.getS2());
        distribution.put(3, s.getS3());
        distribution.put(4, s.getS4());
        distribution.put(5, s.getS5());
        resp.setDistribution(distribution);
        return resp;
    }

    @Data
    static class RatingSummary {
        private String id;
        private double average;
        private long votes;
        private long s1;
        private long s2;
        private long s3;
        private long s4;
        private long s5;
    }
}
