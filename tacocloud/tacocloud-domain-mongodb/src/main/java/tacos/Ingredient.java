package tacos;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;
import java.util.HashSet;

@Data
@NoArgsConstructor(access=AccessLevel.PRIVATE, force=true)
@Document
public class Ingredient {

  public Ingredient(String id, String name) {
    this.id = id;
    this.name = name;
  }
  
  public Ingredient(String id, String name, Type type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }
  
  public Ingredient(String id, String name, Type type, java.math.BigDecimal unitPrice, boolean available, int stockOnHand, int reorderLevel, Long version) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.unitPrice = unitPrice;
    this.available = available;
    this.stockOnHand = stockOnHand;
    this.reorderLevel = reorderLevel;
    this.version = version;
  }

  @Id
  private String id;
  private String name;
  private Type type;
  
  private java.math.BigDecimal unitPrice;
  private boolean available;
  private int stockOnHand;
  private int reorderLevel;
  
  @org.springframework.data.annotation.Version
  private Long version;
  public enum Type {
    WRAP, PROTEIN, VEGGIES, CHEESE, SAUCE
  }

  public enum DietaryTag {
    VEGAN, VEGETARIAN, GLUTEN_FREE, KETO, PALEO
  }

  public enum Allergen {
    DAIRY, SOY, WHEAT, NUTS, EGGS
  }

  public enum SpiceLevel {
    NONE, MILD, MEDIUM, HOT, GHOST_PEPPER
  }

  private Set<DietaryTag> dietaryTags = new HashSet<>();
  private Set<Allergen> allergens = new HashSet<>();
  private SpiceLevel spiceLevel = SpiceLevel.NONE;

}
