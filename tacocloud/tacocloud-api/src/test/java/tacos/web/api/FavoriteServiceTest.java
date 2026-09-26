package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Favorite;
import tacos.Taco;
import tacos.api.error.NotFoundException;
import tacos.data.FavoriteRepository;
import tacos.data.TacoRepository;
import tacos.testsupport.Fixtures;

// TC-21: favoritos por usuario autenticado, idempotentes.
class FavoriteServiceTest {

    private FavoriteRepository favorites;
    private TacoRepository tacos;
    private FavoriteService service;

    @BeforeEach
    void setUp() {
        favorites = mock(FavoriteRepository.class);
        tacos = mock(TacoRepository.class);
        service = new FavoriteService(favorites, tacos, Fixtures.CLOCK, 50);
        when(tacos.existsById(anyString())).thenReturn(Mono.just(false));
        when(tacos.existsById("TACO1")).thenReturn(Mono.just(true));
        when(favorites.findByUserIdAndTacoId(anyString(), anyString())).thenReturn(Mono.empty());
        when(favorites.save(any())).thenAnswer(inv -> Mono.justOrEmpty((Favorite) inv.getArgument(0)));
    }

    @Test
    void addingTwiceKeepsASingleFavorite() {
        Favorite existing = new Favorite("f1", Fixtures.USER_ID, "TACO1", Instant.now());
        StepVerifier.create(service.add(Fixtures.USER_ID, "TACO1")).verifyComplete();
        when(favorites.findByUserIdAndTacoId(Fixtures.USER_ID, "TACO1")).thenReturn(Mono.just(existing));
        StepVerifier.create(service.add(Fixtures.USER_ID, "TACO1")).verifyComplete();

        verify(favorites, org.mockito.Mockito.times(1)).save(any());
    }

    // Dos PUT concurrentes: el índice único rechaza el segundo y se trata como éxito.
    @Test
    void concurrentDuplicateIsIdempotentSuccess() {
        when(favorites.save(any())).thenReturn(Mono.error(new DuplicateKeyException("E11000")));

        StepVerifier.create(service.add(Fixtures.USER_ID, "TACO1")).verifyComplete();
    }

    @Test
    void unknownTacoIs404() {
        StepVerifier.create(service.add(Fixtures.USER_ID, "NOPE")).expectError(NotFoundException.class).verify();
        verify(favorites, never()).save(any());
    }

    @Test
    void deleteIsIdempotentAndScopedToTheUser() {
        when(favorites.deleteByUserIdAndTacoId(anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.remove(Fixtures.USER_ID, "TACO1")).verifyComplete();
        StepVerifier.create(service.remove(Fixtures.USER_ID, "TACO1")).verifyComplete();
        verify(favorites, org.mockito.Mockito.times(2)).deleteByUserIdAndTacoId(Fixtures.USER_ID, "TACO1");
    }

    @Test
    void listsOnlyTheUsersFavoritesAndCleansOrphans() {
        Favorite live = new Favorite("f1", Fixtures.USER_ID, "TACO1", Instant.now());
        Favorite orphan = new Favorite("f2", Fixtures.USER_ID, "GONE", Instant.now());
        Taco taco = new Taco();
        taco.setId("TACO1");
        taco.setName("Carnivore");
        when(favorites.findByUserId(eq(Fixtures.USER_ID), any(Pageable.class)))
            .thenReturn(Flux.just(live, orphan), Flux.empty());
        when(tacos.findById("TACO1")).thenReturn(Mono.just(taco));
        when(tacos.findById("GONE")).thenReturn(Mono.empty());
        when(favorites.delete(orphan)).thenReturn(Mono.empty());

        StepVerifier.create(service.list(Fixtures.USER_ID, 0, 20))
            .assertNext(page -> assertThat(page.getContent()).extracting("tacoId").containsExactly("TACO1"))
            .verifyComplete();
        verify(favorites).delete(orphan);
        verify(favorites, never()).findByUserId(eq(Fixtures.OTHER_USER_ID), any(Pageable.class));
    }
}
