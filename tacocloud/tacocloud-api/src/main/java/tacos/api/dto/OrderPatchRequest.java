package tacos.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * TC-04: lista blanca de campos editables por PATCH (sólo datos de entrega).
 * Cualquier otro campo se captura en {@code rejectedFields} y provoca 400.
 */
@Data
public class OrderPatchRequest {
  @Size(min = 1, max = 50)
  private String deliveryName;
  @Size(min = 1, max = 100)
  private String deliveryStreet;
  @Size(min = 1, max = 50)
  private String deliveryCity;
  @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter state code")
  private String deliveryState;
  @Pattern(regexp = "\\d{5}", message = "must be a 5-digit zip code")
  private String deliveryZip;

  @JsonIgnore
  private Map<String, Object> rejectedFields = new LinkedHashMap<>();

  @JsonAnySetter
  public void rejectField(String name, Object value) {
    rejectedFields.put(name, value);
  }
}
