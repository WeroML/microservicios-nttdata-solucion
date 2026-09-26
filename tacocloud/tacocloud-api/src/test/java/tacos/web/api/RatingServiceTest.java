package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.TacoRating;
import tacos.api.dto.RatingRequest;
import tacos.api.error.BadRequestException;
import tacos.api.error.NotFoundException;
import tacos.data.TacoRepository;
import tacos.testsupport.Fixtures;

// TC-22: calificaciones (el ranking con agregación real se prueba en la suite de integración).
class RatingServiceTest {

    private ReactiveMongoTemplate mongo;
    private TacoRepository tacos;
    private RatingService service;

    @BeforeEach
    void setUp() {
        mongo = mock(ReactiveMongoTemplate.class);
        tacos = mock(TacoRepository.class);
        service = new RatingService(mongo, tacos, new RatingProperties(), Fixtures.CLOCK);
        when(tacos.existsById(anyString())).thenReturn(Mono.just(false));
        when(tacos.existsById("TACO1")).thenReturn(Mono.just(true));
        when(mongo.upsert(any(Query.class), any(Update.class), eq(TacoRating.class)))
            .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
    }

    @Test
    void voteIsAnUpsertKeyedByTheAuthenticatedUserAndTaco() {
        StepVerifier.create(service.rate("TACO1", Fixtures.USER_ID, 4)).verifyComplete();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).upsert(query.capture(), update.capture(), eq(TacoRating.class));
        assertThat(query.getValue().getQueryObject().get("userId")).isEqualTo(Fixtures.USER_ID);
        assertThat(query.getValue().getQueryObject().get("tacoId")).isEqualTo("TACO1");
        org.bson.Document set = (org.bson.Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("score")).isEqualTo(4);
    }

    @Test
    void concurrentFirstVoteRetriesAsUpdate() {
        when(mongo.upsert(any(Query.class), any(Update.class), eq(TacoRating.class)))
            .thenReturn(Mono.error(new DuplicateKeyException("E11000")))
            .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(service.rate("TACO1", Fixtures.USER_ID, 5)).verifyComplete();
        verify(mongo, times(2)).upsert(any(Query.class), any(Update.class), eq(TacoRating.class));
    }

    @Test
    void cannotRateAnUnknownTaco() {
        StepVerifier.create(service.rate("NOPE", Fixtures.USER_ID, 5)).expectError(NotFoundException.class).verify();
        verify(mongo, never()).upsert(any(Query.class), any(Update.class), eq(TacoRating.class));
    }

    @Test
    void scoreOutOfRangeIsInvalid() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        for (int score : new int[] {0, 6}) {
            RatingRequest request = new RatingRequest();
            request.setScore(score);
            Set<?> violations = validator.validate(request);
            assertThat(violations).hasSize(1);
        }
    }

    @Test
    void rankingLimitIsBounded() {
        StepVerifier.create(service.top(0)).expectError(BadRequestException.class).verify();
        StepVerifier.create(service.top(51)).expectError(BadRequestException.class).verify();
    }
}
