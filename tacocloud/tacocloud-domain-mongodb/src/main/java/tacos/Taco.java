package tacos;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.rest.core.annotation.RestResource;

import lombok.Data;

@Data
@RestResource(rel = "tacos", path = "tacos")
@Document
// TC-19: índices acordes a los filtros principales de búsqueda.
@CompoundIndex(name = "ingredient_idx", def = "{'ingredients._id': 1}")
public class Taco {

  @Id
  private String id;

  @NotNull
  @Size(min = 5, message = "Name must be at least 5 characters long")
  @Indexed
  private String name;

  @Indexed
  private Date createdAt = new Date();

  @Size(min=1, message="You must choose at least 1 ingredient")
  private List<Ingredient> ingredients;

  // TC-17: valores derivados de los ingredientes por ClassificationService;
  // nunca se aceptan del cliente.
  @Indexed
  private Set<Ingredient.DietaryTag> dietaryTags = new HashSet<>();
  @Indexed
  private Set<Ingredient.Allergen> allergens = new HashSet<>();
  @Indexed
  private Ingredient.SpiceLevel spiceLevel = Ingredient.SpiceLevel.NONE;

}
