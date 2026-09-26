package tacos.testsupport;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;
import tacos.Ingredient.Type;
import tacos.PaymentMethod;
import tacos.Taco;
import tacos.User;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.dto.TacoRequest;
import tacos.classification.ClassificationService;
import tacos.data.IngredientRepository;
import tacos.data.PaymentMethodRepository;
import tacos.discount.DiscountProperties;
import tacos.discount.DiscountService;
import tacos.pricing.PricingProperties;
import tacos.pricing.PricingService;
import tacos.validation.AvailableIngredientsRule;
import tacos.validation.IngredientCountRule;
import tacos.validation.NoDuplicateIngredientsRule;
import tacos.validation.SingleBaseRule;
import tacos.validation.SpicyNeedsCheeseRule;
import tacos.validation.TacoDesignService;
import tacos.validation.TacoRule;
import tacos.validation.TacoRulesProperties;
import tacos.validation.TacoValidatorService;
import tacos.web.api.Actor;
import tacos.web.api.OrderDraftFactory;
import tacos.validation.VeganNameRule;

/** Datos sintéticos y servicios reales con repositorios simulados. */
public final class Fixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T18:00:00Z"), ZoneOffset.UTC);
    public static final String USER_ID = "user-a";
    public static final String OTHER_USER_ID = "user-b";
    public static final String PAYMENT_ID = "pm-a";

    private static final EnumSet<DietaryTag> VEGAN = EnumSet.of(DietaryTag.VEGAN, DietaryTag.VEGETARIAN);
    private static final EnumSet<DietaryTag> VEGAN_GF = EnumSet.of(DietaryTag.VEGAN, DietaryTag.VEGETARIAN, DietaryTag.GLUTEN_FREE);
    private static final EnumSet<DietaryTag> GF = EnumSet.of(DietaryTag.GLUTEN_FREE);
    private static final EnumSet<DietaryTag> VEG_GF = EnumSet.of(DietaryTag.VEGETARIAN, DietaryTag.GLUTEN_FREE);
    private static final EnumSet<Allergen> NONE = EnumSet.noneOf(Allergen.class);

    private Fixtures() {
    }

    public static Ingredient ingredient(String id, Type type, String price, Set<DietaryTag> tags,
                                        Set<Allergen> allergens, SpiceLevel spice) {
        Ingredient i = new Ingredient(id, id + " name", type);
        i.setUnitPrice(new BigDecimal(price));
        i.setAvailable(true);
        i.setStockOnHand(100);
        i.setDietaryTags(new HashSet<>(tags));
        i.setAllergens(new HashSet<>(allergens));
        i.setSpiceLevel(spice);
        return i;
    }

    public static Map<String, Ingredient> catalog() {
        Map<String, Ingredient> catalog = new HashMap<>();
        put(catalog, ingredient("FLTO", Type.WRAP, "0.50", VEGAN, EnumSet.of(Allergen.WHEAT), SpiceLevel.NONE));
        put(catalog, ingredient("COTO", Type.WRAP, "0.45", VEGAN_GF, NONE, SpiceLevel.NONE));
        put(catalog, ingredient("GRBF", Type.PROTEIN, "1.25", GF, NONE, SpiceLevel.NONE));
        put(catalog, ingredient("CARN", Type.PROTEIN, "1.40", GF, NONE, SpiceLevel.MILD));
        put(catalog, ingredient("TMTO", Type.VEGGIES, "0.30", VEGAN_GF, NONE, SpiceLevel.NONE));
        put(catalog, ingredient("CHED", Type.CHEESE, "0.60", VEG_GF, EnumSet.of(Allergen.DAIRY), SpiceLevel.NONE));
        put(catalog, ingredient("SLSA", Type.SAUCE, "0.35", VEGAN_GF, NONE, SpiceLevel.MEDIUM));
        put(catalog, ingredient("SOYS", Type.SAUCE, "0.15", VEGAN_GF, EnumSet.of(Allergen.SOY), SpiceLevel.NONE));
        put(catalog, ingredient("GHST", Type.SAUCE, "0.20", VEGAN_GF, NONE, SpiceLevel.GHOST_PEPPER));
        return catalog;
    }

    private static void put(Map<String, Ingredient> catalog, Ingredient i) {
        catalog.put(i.getId(), i);
    }

    public static List<Ingredient> ingredients(Map<String, Ingredient> catalog, String... ids) {
        return Arrays.stream(ids).map(catalog::get).collect(Collectors.toList());
    }

    public static Taco taco(String id, String name, Map<String, Ingredient> catalog, String... ingredientIds) {
        Taco taco = new Taco();
        taco.setId(id);
        taco.setName(name);
        taco.setIngredients(ingredients(catalog, ingredientIds));
        return taco;
    }

    public static IngredientRepository ingredientRepo(Map<String, Ingredient> catalog) {
        IngredientRepository repo = mock(IngredientRepository.class);
        when(repo.findById(anyString())).thenAnswer(inv -> Mono.justOrEmpty(catalog.get((String) inv.getArgument(0))));
        return repo;
    }

    public static TacoRulesProperties ruleProperties() {
        return new TacoRulesProperties();
    }

    public static List<TacoRule> rules() {
        TacoRulesProperties props = ruleProperties();
        return Arrays.asList(new SingleBaseRule(), new IngredientCountRule(), new NoDuplicateIngredientsRule(),
            new AvailableIngredientsRule(), new SpicyNeedsCheeseRule(props), new VeganNameRule(props));
    }

    public static TacoValidatorService validator() {
        return new TacoValidatorService(rules());
    }

    public static TacoDesignService designService(IngredientRepository repo) {
        return new TacoDesignService(repo, validator(), new ClassificationService());
    }

    public static PricingService pricing() {
        PricingProperties props = new PricingProperties();
        props.setCurrency("USD");
        props.setMaxQuantityPerLine(10);
        return new PricingService(props);
    }

    public static DiscountProperties.Coupon coupon(DiscountProperties.Type type, String amount, String from,
                                                   String until, String min, String max) {
        DiscountProperties.Coupon c = new DiscountProperties.Coupon();
        c.setType(type);
        c.setAmount(new BigDecimal(amount));
        c.setValidFrom(from == null ? null : LocalDate.parse(from));
        c.setValidUntil(until == null ? null : LocalDate.parse(until));
        c.setMinPurchase(new BigDecimal(min));
        c.setMaxDiscount(max == null ? null : new BigDecimal(max));
        return c;
    }

    public static DiscountService discountService(Clock clock) {
        DiscountProperties props = new DiscountProperties();
        props.getCodes().put("abcdef", coupon(DiscountProperties.Type.PERCENTAGE, "0.10", "2026-01-01", "2026-12-31", "0", "5.00"));
        props.getCodes().put("TACO2", coupon(DiscountProperties.Type.FIXED, "2.00", "2026-01-01", "2026-12-31", "5.00", null));
        props.getCodes().put("OLD", coupon(DiscountProperties.Type.FIXED, "1.00", "2020-01-01", "2020-12-31", "0", null));
        props.getCodes().put("FUTURE", coupon(DiscountProperties.Type.FIXED, "1.00", "2027-01-01", "2027-12-31", "0", null));
        props.getCodes().put("HALF", coupon(DiscountProperties.Type.PERCENTAGE, "0.50", "2026-01-01", "2026-12-31", "0", "3.00"));
        props.getCodes().put("BIGFIX", coupon(DiscountProperties.Type.FIXED, "100.00", "2026-01-01", "2026-12-31", "0", null));
        return new DiscountService(props, clock);
    }

    public static PaymentMethodRepository paymentRepo() {
        PaymentMethodRepository repo = mock(PaymentMethodRepository.class);
        PaymentMethod pm = new PaymentMethod(USER_ID, "tok_test_1234", "VISA", "1111", "10/30");
        pm.setId(PAYMENT_ID);
        when(repo.findByIdAndUserId(anyString(), anyString())).thenReturn(Mono.empty());
        when(repo.findByIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Mono.just(pm));
        when(repo.findByUserId(anyString())).thenReturn(reactor.core.publisher.Flux.empty());
        when(repo.findByUserId(USER_ID)).thenReturn(reactor.core.publisher.Flux.just(pm));
        return repo;
    }

    public static OrderDraftFactory draftFactory(Map<String, Ingredient> catalog) {
        return new OrderDraftFactory(designService(ingredientRepo(catalog)), pricing(), discountService(CLOCK),
            paymentRepo(), CLOCK);
    }

    public static TacoRequest tacoRequest(String name, String... ingredientIds) {
        TacoRequest taco = new TacoRequest();
        taco.setName(name);
        taco.setIngredientIds(Arrays.asList(ingredientIds));
        return taco;
    }

    public static OrderCreateRequest.OrderItemRequest item(TacoRequest taco, int quantity) {
        OrderCreateRequest.OrderItemRequest item = new OrderCreateRequest.OrderItemRequest();
        item.setTaco(taco);
        item.setQuantity(quantity);
        return item;
    }

    public static OrderCreateRequest orderRequest(OrderCreateRequest.OrderItemRequest... items) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setDeliveryName("Craig");
        request.setDeliveryStreet("123 North Street");
        request.setDeliveryCity("Cross Roads");
        request.setDeliveryState("TX");
        request.setDeliveryZip("76227");
        request.setPaymentMethodId(PAYMENT_ID);
        request.setItems(Arrays.asList(items));
        return request;
    }

    public static OrderCreateRequest simpleOrderRequest() {
        return orderRequest(item(tacoRequest("Classic", "FLTO", "GRBF", "CHED"), 1));
    }

    public static Actor user(String userId) {
        return new Actor(userId, userId + "-name", Collections.singleton(User.ROLE_USER));
    }

    public static Actor admin() {
        return new Actor("admin-id", "admin", Collections.singleton(User.ROLE_ADMIN));
    }

    public static Actor kitchen() {
        return new Actor("kitchen-id", "kitchen", Collections.singleton(User.ROLE_KITCHEN));
    }

    public static Authentication authentication(String userId, String... roles) {
        String[] authorities = Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new);
        return new TestingAuthenticationToken(userId, "n/a", authorities);
    }
}
