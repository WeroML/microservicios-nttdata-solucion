package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;

import reactor.core.publisher.Flux;
import tacos.Ingredient;
import tacos.Taco;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.TacoMapper;
import tacos.classification.ClassificationService;
import tacos.data.TacoRepository;
import tacos.data.TacoSearchCriteria;
import tacos.data.TacoSearchRepositoryImpl;
import tacos.testsupport.Fixtures;
import tacos.testsupport.Mvc;

// TC-19: buscar, filtrar, ordenar y paginar con límites seguros.
class TacoSearchTest {

    private TacoRepository tacoRepo;
    private MockMvc mvc;
    private Map<String, Ingredient> catalog;

    @BeforeEach
    void setUp() {
        catalog = Fixtures.catalog();
        tacoRepo = mock(TacoRepository.class);
        TacoSearchProperties props = new TacoSearchProperties();
        props.setMaxPageSize(50);
        mvc = Mvc.standalone(new TacoController(tacoRepo, Fixtures.designService(Fixtures.ingredientRepo(catalog)),
            new ClassificationService(), mock(TacoOfTheDayService.class),
            new TacoMapper(new IngredientMapper()), props));
    }

    @Test
    void emptyQueryReturnsFirstPageSortedByCreatedAtDescThenId() throws Exception {
        Taco t1 = Fixtures.taco("T1", "First", catalog, "FLTO", "GRBF");
        Taco t2 = Fixtures.taco("T2", "Second", catalog, "COTO", "TMTO");
        when(tacoRepo.searchTacos(any(), any(), anyLong(), anyInt())).thenReturn(Flux.just(t1, t2));

        Mvc.perform(mvc, get("/api/v1/tacos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(tacoRepo).searchTacos(any(), sort.capture(), eq(0L), eq(21));
        assertThat(sort.getValue()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("_id"));
    }

    @Test
    void secondPageUsesStableOffsetAndDetectsNextPage() throws Exception {
        Taco[] three = {Fixtures.taco("A", "Aaaaa", catalog, "COTO", "TMTO"),
            Fixtures.taco("B", "Bbbbb", catalog, "COTO", "TMTO"), Fixtures.taco("C", "Ccccc", catalog, "COTO", "TMTO")};
        when(tacoRepo.searchTacos(any(), any(), anyLong(), anyInt())).thenReturn(Flux.just(three));

        Mvc.perform(mvc, get("/api/v1/tacos?page=1&size=2&sort=name,asc"))
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.hasNext").value(true));
        verify(tacoRepo).searchTacos(any(), any(), eq(2L), eq(3));
    }

    @Test
    void excessiveSizeAndUnknownSortAreRejected() throws Exception {
        Mvc.perform(mvc, get("/api/v1/tacos?size=500"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        Mvc.perform(mvc, get("/api/v1/tacos?sort=password,asc"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SORT"));
        Mvc.perform(mvc, get("/api/v1/tacos?diet=CARNIVORE"))
            .andExpect(status().isBadRequest());
        verify(tacoRepo, never()).searchTacos(any(), any(), anyLong(), anyInt());
    }

    @Test
    void filtersArePassedAsCriteria() throws Exception {
        when(tacoRepo.searchTacos(any(), any(), anyLong(), anyInt())).thenReturn(Flux.empty());

        Mvc.perform(mvc, get("/api/v1/tacos?name=beef&ingredientId=GRBF&diet=GLUTEN_FREE"
                + "&excludeAllergen=DAIRY&excludeAllergen=WHEAT&spice=MILD"))
            .andExpect(status().isOk());

        ArgumentCaptor<TacoSearchCriteria> criteria = ArgumentCaptor.forClass(TacoSearchCriteria.class);
        verify(tacoRepo).searchTacos(criteria.capture(), any(), anyLong(), anyInt());
        assertThat(criteria.getValue().getName()).isEqualTo("beef");
        assertThat(criteria.getValue().getDiet()).isEqualTo(Ingredient.DietaryTag.GLUTEN_FREE);
        assertThat(criteria.getValue().getExcludeAllergens())
            .containsExactly(Ingredient.Allergen.DAIRY, Ingredient.Allergen.WHEAT);
        assertThat(criteria.getValue().getSpice()).isEqualTo(Ingredient.SpiceLevel.MILD);
    }

    // La búsqueda se hace en Mongo como intersección y el texto se escapa (sin regex costosa).
    @Test
    void repositoryBuildsAnEscapedIntersectionQuery() {
        ReactiveMongoTemplate template = mock(ReactiveMongoTemplate.class);
        when(template.find(any(Query.class), eq(Taco.class))).thenReturn(Flux.empty());
        TacoSearchCriteria criteria = TacoSearchCriteria.builder()
            .name("a.*(b")
            .ingredientId("GRBF")
            .diet(Ingredient.DietaryTag.VEGAN)
            .excludeAllergens(Arrays.asList(Ingredient.Allergen.DAIRY))
            .spice(Ingredient.SpiceLevel.HOT)
            .build();

        new TacoSearchRepositoryImpl(template).searchTacos(criteria, Sort.by("name"), 40, 21).blockLast();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).find(query.capture(), eq(Taco.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q.keySet()).containsExactlyInAnyOrder("name", "ingredients._id", "dietaryTags", "allergens", "spiceLevel");
        assertThat(q.get("name").toString()).contains("\\Qa.*(b\\E");
        assertThat(query.getValue().getSkip()).isEqualTo(40);
        assertThat(query.getValue().getLimit()).isEqualTo(21);
    }
}
