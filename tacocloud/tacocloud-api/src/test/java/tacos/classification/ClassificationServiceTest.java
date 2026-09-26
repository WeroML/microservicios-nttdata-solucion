package tacos.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;
import tacos.Taco;
import tacos.testsupport.Fixtures;

// TC-17: etiquetas, alérgenos y picante derivados de los ingredientes.
class ClassificationServiceTest {

    private final ClassificationService service = new ClassificationService();
    private final Map<String, Ingredient> catalog = Fixtures.catalog();

    @Test
    void oneNonVeganIngredientMakesTheTacoNonVegan() {
        ClassificationService.TacoClassification c =
            service.classify(Fixtures.ingredients(catalog, "COTO", "TMTO", "CHED"));

        assertThat(c.dietaryTags).doesNotContain(DietaryTag.VEGAN);
        assertThat(c.dietaryTags).containsExactlyInAnyOrder(DietaryTag.VEGETARIAN, DietaryTag.GLUTEN_FREE);
    }

    @Test
    void allVeganIngredientsMakeAVeganTaco() {
        assertThat(service.classify(Fixtures.ingredients(catalog, "COTO", "TMTO", "SLSA")).dietaryTags)
            .contains(DietaryTag.VEGAN, DietaryTag.GLUTEN_FREE);
    }

    @Test
    void allergensAreTheExactUnion() {
        assertThat(service.classify(Fixtures.ingredients(catalog, "FLTO", "CHED", "SOYS", "TMTO")).allergens)
            .containsExactlyInAnyOrder(Allergen.WHEAT, Allergen.DAIRY, Allergen.SOY);
    }

    @Test
    void spiceIsTheHighestIngredientSpiceRegardlessOfOrder() {
        assertThat(service.classify(Fixtures.ingredients(catalog, "SLSA", "CARN", "TMTO")).spiceLevel)
            .isEqualTo(SpiceLevel.MEDIUM);
        assertThat(service.classify(Fixtures.ingredients(catalog, "TMTO", "CARN", "SLSA")).spiceLevel)
            .isEqualTo(SpiceLevel.MEDIUM);
    }

    @Test
    void responseCarriesTheAcademicDisclaimer() {
        Taco taco = Fixtures.taco("T1", "Veggie", catalog, "COTO", "TMTO");
        assertThat(service.toResponse(taco).getDisclaimer()).contains("cross-contamination");
    }

    // Mass assignment: el cliente sólo manda IDs; las etiquetas salen del catálogo.
    @Test
    void clientCannotForgeTags() {
        StepVerifier.create(Fixtures.designService(Fixtures.ingredientRepo(catalog))
                .design(Fixtures.tacoRequest("Beef taco", "COTO", "GRBF")))
            .assertNext(taco -> assertThat(taco.getDietaryTags()).containsExactly(DietaryTag.GLUTEN_FREE))
            .verifyComplete();
    }
}
