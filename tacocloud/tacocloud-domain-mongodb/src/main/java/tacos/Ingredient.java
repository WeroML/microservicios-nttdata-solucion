package tacos;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access=AccessLevel.PRIVATE, force=true)
@Document
public class Ingredient {

  public Ingredient(String id, String name, Type type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  @Id
  private String id;
  private String name;
  private Type type;

  // TC-13: datos de catálogo e inventario. El dinero siempre en BigDecimal.
  private BigDecimal unitPrice;
  private boolean available;
  private int stockOnHand;
  private int reorderLevel;

  @Version
  private Long version;

  // TC-17: metadata del ingrediente. La clasificación del taco se deriva de aquí.
  private Set<DietaryTag> dietaryTags = new HashSet<>();
  private Set<Allergen> allergens = new HashSet<>();
  private SpiceLevel spiceLevel = SpiceLevel.NONE;

  public enum Type {
    WRAP, PROTEIN, VEGGIES, CHEESE, SAUCE
  }

  public enum DietaryTag {
    VEGAN, VEGETARIAN, GLUTEN_FREE
  }

  public enum Allergen {
    DAIRY, SOY, WHEAT, NUTS, EGGS
  }

  public enum SpiceLevel {
    NONE, MILD, MEDIUM, HOT, GHOST_PEPPER
  }

}
