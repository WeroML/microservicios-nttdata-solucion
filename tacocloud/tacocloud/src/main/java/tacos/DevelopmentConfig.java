package tacos;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient.Allergen;
import tacos.Ingredient.DietaryTag;
import tacos.Ingredient.SpiceLevel;
import tacos.Ingredient.Type;
import tacos.classification.ClassificationService;
import tacos.data.IngredientRepository;
import tacos.data.PaymentMethodRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;

/**
 * Datos de desarrollo coherentes con las reglas del catálogo (TC-13), la
 * clasificación (TC-17), las reglas de diseño (TC-18), los roles (TC-11) y los
 * métodos de pago tokenizados (TC-12). Son valores sintéticos sólo para laboratorio.
 */
@Profile("!prod")
@Configuration
public class DevelopmentConfig {

  private static final EnumSet<DietaryTag> VEGAN = EnumSet.of(DietaryTag.VEGAN, DietaryTag.VEGETARIAN);
  private static final EnumSet<DietaryTag> VEGAN_GF = EnumSet.of(DietaryTag.VEGAN, DietaryTag.VEGETARIAN, DietaryTag.GLUTEN_FREE);
  private static final EnumSet<DietaryTag> VEGETARIAN_GF = EnumSet.of(DietaryTag.VEGETARIAN, DietaryTag.GLUTEN_FREE);
  private static final EnumSet<DietaryTag> GLUTEN_FREE = EnumSet.of(DietaryTag.GLUTEN_FREE);

  @Bean
  public CommandLineRunner dataLoader(IngredientRepository repo,
        UserRepository userRepo, PasswordEncoder encoder, TacoRepository tacoRepo,
        PaymentMethodRepository paymentMethodRepo, ClassificationService classificationService) {

    return args -> {
      Ingredient flourTortilla = ingredient("FLTO", "Flour Tortilla", Type.WRAP, "0.50", VEGAN, EnumSet.of(Allergen.WHEAT), SpiceLevel.NONE);
      Ingredient cornTortilla = ingredient("COTO", "Corn Tortilla", Type.WRAP, "0.45", VEGAN_GF, EnumSet.noneOf(Allergen.class), SpiceLevel.NONE);
      Ingredient groundBeef = ingredient("GRBF", "Ground Beef", Type.PROTEIN, "1.25", GLUTEN_FREE, EnumSet.noneOf(Allergen.class), SpiceLevel.NONE);
      Ingredient carnitas = ingredient("CARN", "Carnitas", Type.PROTEIN, "1.40", GLUTEN_FREE, EnumSet.noneOf(Allergen.class), SpiceLevel.MILD);
      Ingredient tomatoes = ingredient("TMTO", "Diced Tomatoes", Type.VEGGIES, "0.30", VEGAN_GF, EnumSet.noneOf(Allergen.class), SpiceLevel.NONE);
      Ingredient lettuce = ingredient("LETC", "Lettuce", Type.VEGGIES, "0.25", VEGAN_GF, EnumSet.noneOf(Allergen.class), SpiceLevel.NONE);
      Ingredient cheddar = ingredient("CHED", "Cheddar", Type.CHEESE, "0.60", VEGETARIAN_GF, EnumSet.of(Allergen.DAIRY), SpiceLevel.NONE);
      Ingredient jack = ingredient("JACK", "Monterrey Jack", Type.CHEESE, "0.65", VEGETARIAN_GF, EnumSet.of(Allergen.DAIRY), SpiceLevel.NONE);
      Ingredient salsa = ingredient("SLSA", "Salsa", Type.SAUCE, "0.35", VEGAN_GF, EnumSet.noneOf(Allergen.class), SpiceLevel.MEDIUM);
      Ingredient sourCream = ingredient("SRCR", "Sour Cream", Type.SAUCE, "0.40", VEGETARIAN_GF, EnumSet.of(Allergen.DAIRY), SpiceLevel.NONE);
      List<Ingredient> ingredients = Arrays.asList(flourTortilla, cornTortilla, groundBeef, carnitas,
          tomatoes, lettuce, cheddar, jack, salsa, sourCream);

      Taco taco1 = taco("TACO1", "Carnivore", classificationService,
          flourTortilla, groundBeef, carnitas, sourCream, salsa, cheddar);
      Taco taco2 = taco("TACO2", "Bovine Bounty", classificationService,
          cornTortilla, groundBeef, cheddar, jack, sourCream);
      Taco taco3 = taco("TACO3", "Veg-Out", classificationService,
          cornTortilla, tomatoes, lettuce, salsa);

      User customer = user("habuma", "password", "Craig Walls", "craig@habuma.com", encoder, User.ROLE_USER);
      User admin = user("admin", "password", "Taco Admin", "admin@tacocloud.test", encoder, User.ROLE_ADMIN);
      User cook = user("kitchen", "password", "Taco Cook", "kitchen@tacocloud.test", encoder, User.ROLE_KITCHEN);

      // Borde de arranque: la carga termina antes de que la aplicación atienda peticiones.
      Flux.fromIterable(ingredients).concatMap(repo::save)
          .thenMany(Flux.just(taco1, taco2, taco3).concatMap(tacoRepo::save))
          .thenMany(Flux.just(admin, cook).concatMap(userRepo::save))
          .then(userRepo.save(customer))
          .flatMap(saved -> paymentMethodRepo.save(
              new PaymentMethod(saved.getId(), "tok_dev_visa_1111", "VISA", "1111", "10/30")))
          .then(Mono.empty())
          .block();
    };
  }

  private static Ingredient ingredient(String id, String name, Type type, String price,
      EnumSet<DietaryTag> tags, EnumSet<Allergen> allergens, SpiceLevel spice) {
    Ingredient ingredient = new Ingredient(id, name, type);
    ingredient.setUnitPrice(new BigDecimal(price));
    ingredient.setAvailable(true);
    ingredient.setStockOnHand(100);
    ingredient.setReorderLevel(10);
    ingredient.setDietaryTags(new HashSet<>(tags));
    ingredient.setAllergens(new HashSet<>(allergens));
    ingredient.setSpiceLevel(spice);
    return ingredient;
  }

  private static Taco taco(String id, String name, ClassificationService classificationService,
      Ingredient... ingredients) {
    Taco taco = new Taco();
    taco.setId(id);
    taco.setName(name);
    taco.setIngredients(Arrays.asList(ingredients));
    classificationService.applyTo(taco);
    return taco;
  }

  private static User user(String username, String password, String fullname, String email,
      PasswordEncoder encoder, String role) {
    User user = new User(username, encoder.encode(password), fullname, "123 North Street",
        "Cross Roads", "TX", "76227", "123-123-1234", email);
    user.setRoles(new HashSet<>(Collections.singleton(role)));
    return user;
  }

}
