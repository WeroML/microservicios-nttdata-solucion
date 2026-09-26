package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.Taco;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.TacoMapper;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;
import tacos.testsupport.Fixtures;

// TC-20: taco del día determinista y comprobable con FixedClock.
class TacoOfTheDayServiceTest {

    private Map<String, Ingredient> catalog;
    private TacoRepository tacoRepo;
    private IngredientRepository ingredientRepo;
    private Taco a;
    private Taco b;
    private Taco c;

    @BeforeEach
    void setUp() {
        catalog = Fixtures.catalog();
        tacoRepo = mock(TacoRepository.class);
        ingredientRepo = mock(IngredientRepository.class);
        when(ingredientRepo.findAll()).thenAnswer(inv -> Flux.fromIterable(catalog.values()));
        a = Fixtures.taco("A", "Alpha", catalog, "FLTO", "GRBF");
        b = Fixtures.taco("B", "Bravo", catalog, "COTO", "TMTO");
        c = Fixtures.taco("C", "Charlie", catalog, "COTO", "CARN");
    }

    private TacoOfTheDayService service(String instant) {
        return new TacoOfTheDayService(tacoRepo, ingredientRepo, Fixtures.validator(),
            new TacoMapper(new IngredientMapper()), Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private String todayId(String instant) {
        return service(instant).getTacoOfTheDay().block().getTaco().getId();
    }

    @Test
    void sameDayReturnsSameTacoAndNextDayIsPredictable() {
        when(tacoRepo.findAll()).thenReturn(Flux.just(a, b, c));
        // 2026-09-25 = epochDay 20721; 20721 mod 3 = 0 -> A; al día siguiente -> B
        assertThat(todayId("2026-09-25T01:00:00Z")).isEqualTo("A");
        assertThat(todayId("2026-09-25T23:00:00Z")).isEqualTo("A");
        assertThat(todayId("2026-09-26T08:00:00Z")).isEqualTo("B");
    }

    @Test
    void physicalOrderDoesNotChangeTheResult() {
        when(tacoRepo.findAll()).thenReturn(Flux.just(c, a, b));
        String shuffled = todayId("2026-09-26T08:00:00Z");
        when(tacoRepo.findAll()).thenReturn(Flux.just(a, b, c));
        assertThat(todayId("2026-09-26T08:00:00Z")).isEqualTo(shuffled);
    }

    @Test
    void noCandidatesReturnsEmpty() {
        when(tacoRepo.findAll()).thenReturn(Flux.empty());
        StepVerifier.create(service("2026-09-25T12:00:00Z").getTacoOfTheDay()).verifyComplete();
    }

    @Test
    void unavailableTacoIsNeverRecommended() {
        when(tacoRepo.findAll()).thenReturn(Flux.just(a, b, c));
        catalog.get("TMTO").setAvailable(false); // B deja de estar disponible
        for (int day = 0; day < 6; day++) {
            String instant = Instant.parse("2026-09-25T12:00:00Z").plusSeconds(86400L * day).toString();
            assertThat(todayId(instant)).isNotEqualTo("B");
        }
    }

    @Test
    void explainsTheReason() {
        when(tacoRepo.findAll()).thenReturn(Flux.just(a));
        assertThat(service("2026-09-25T12:00:00Z").getTacoOfTheDay().block().getReason())
            .contains("2026-09-25", "FRIDAY");
    }
}
