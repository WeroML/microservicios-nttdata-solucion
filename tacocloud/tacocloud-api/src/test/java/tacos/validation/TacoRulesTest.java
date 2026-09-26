package tacos.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.Taco;
import tacos.api.error.ApiException;
import tacos.testsupport.Fixtures;

// TC-18: reglas componibles de diseño.
class TacoRulesTest {

    private final Map<String, Ingredient> catalog = Fixtures.catalog();

    private Taco taco(String name, String... ids) {
        return Fixtures.taco(null, name, catalog, ids);
    }

    private static List<String> codes(List<RuleViolation> violations) {
        List<String> codes = new ArrayList<>();
        violations.forEach(v -> codes.add(v.getCode()));
        return codes;
    }

    @Test
    void singleBase() {
        SingleBaseRule rule = new SingleBaseRule();
        assertThat(rule.validate(taco("Wrapped", "FLTO", "GRBF"))).isEmpty();
        assertThat(rule.validate(taco("Bowl", "GRBF", "TMTO"))).isEmpty();
        assertThat(codes(rule.validate(taco("Double", "FLTO", "COTO", "GRBF")))).containsExactly("MULTIPLE_BASES");
    }

    @Test
    void ingredientCountBetweenTwoAndTwelve() {
        IngredientCountRule rule = new IngredientCountRule();
        assertThat(codes(rule.validate(taco("Lonely", "GRBF")))).containsExactly("INGREDIENT_COUNT");
        assertThat(rule.validate(taco("Pair", "GRBF", "TMTO"))).isEmpty();
        Taco huge = taco("Huge", "GRBF");
        List<Ingredient> thirteen = new ArrayList<>(Collections.nCopies(13, catalog.get("TMTO")));
        huge.setIngredients(thirteen);
        assertThat(codes(rule.validate(huge))).containsExactly("INGREDIENT_COUNT");
    }

    @Test
    void noDuplicates() {
        assertThat(codes(new NoDuplicateIngredientsRule().validate(taco("Beefy", "GRBF", "GRBF", "TMTO"))))
            .containsExactly("DUPLICATE_INGREDIENT");
    }

    @Test
    void onlyAvailableIngredients() {
        catalog.get("CHED").setAvailable(false);
        assertThat(codes(new AvailableIngredientsRule().validate(taco("Cheesy", "GRBF", "CHED"))))
            .containsExactly("INGREDIENT_UNAVAILABLE");
    }

    @Test
    void spicyNeedsCheeseIsConfigurable() {
        TacoRulesProperties props = new TacoRulesProperties();
        SpicyNeedsCheeseRule rule = new SpicyNeedsCheeseRule(props);
        assertThat(codes(rule.validate(taco("Fire", "GRBF", "GHST")))).containsExactly("SPICY_NEEDS_CHEESE");
        assertThat(rule.validate(taco("Fire", "GRBF", "GHST", "CHED"))).isEmpty();

        props.getSpicyNeedsCheese().setEnabled(false);
        assertThat(rule.validate(taco("Fire", "GRBF", "GHST"))).isEmpty();
    }

    @Test
    void veganNameDoesNotAllowCarnitas() {
        VeganNameRule rule = new VeganNameRule(new TacoRulesProperties());
        assertThat(codes(rule.validate(taco("Vegan dream", "COTO", "CARN")))).containsExactly("VEGAN_NAME_NOT_VEGAN");
        assertThat(rule.validate(taco("Vegan dream", "COTO", "TMTO"))).isEmpty();
    }

    @Test
    void validDesignPassesWithoutViolations() {
        assertThat(Fixtures.validator().validate(taco("Classic", "FLTO", "GRBF", "CHED"))).isEmpty();
    }

    @Test
    void invalidDesignReturnsAllViolationsSortedRegardlessOfRuleOrder() {
        Taco bad = taco("Vegan fire", "FLTO", "COTO", "CARN", "CARN", "GHST");
        List<TacoRule> reversed = new ArrayList<>(Fixtures.rules());
        Collections.reverse(reversed);

        List<RuleViolation> a = Fixtures.validator().validate(bad);
        List<RuleViolation> b = new TacoValidatorService(reversed).validate(bad);

        assertThat(codes(a)).containsExactly("DUPLICATE_INGREDIENT", "MULTIPLE_BASES", "SPICY_NEEDS_CHEESE",
            "VEGAN_NAME_NOT_VEGAN");
        assertThat(a).isEqualTo(b);
    }

    // Agregar una regla nueva no requiere tocar el validador central.
    @Test
    void newRuleIsPickedUpWithoutChangingTheValidator() {
        TacoRule noLettuce = t -> t.getName().contains("Salad")
            ? Collections.singletonList(new RuleViolation("NOT_A_SALAD", "Tacos are not salads."))
            : Collections.emptyList();
        List<TacoRule> rules = new ArrayList<>(Fixtures.rules());
        rules.add(noLettuce);

        assertThat(codes(new TacoValidatorService(rules).validate(taco("Salad taco", "COTO", "TMTO"))))
            .containsExactly("NOT_A_SALAD");
    }

    @Test
    void designServiceRejectsInvalidDesignWith422AndAllViolations() {
        StepVerifier.create(Fixtures.designService(Fixtures.ingredientRepo(catalog))
                .design(Fixtures.tacoRequest("Double", "FLTO", "COTO", "GRBF", "GRBF")))
            .expectErrorSatisfies(e -> {
                ApiException error = (ApiException) e;
                assertThat(error.getStatus().value()).isEqualTo(422);
                assertThat(error.getViolations()).extracting("code")
                    .containsExactly("DUPLICATE_INGREDIENT", "MULTIPLE_BASES");
            })
            .verify();
    }
}
