package tacos.api.dto;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;
import tacos.Ingredient.Type;

/**
 * TC-01/TC-03/TC-08: request de ingrediente.
 * - POST: el id no se envía; lo asigna la persistencia.
 * - PUT: el id del body debe coincidir con el de la ruta.
 * - stockOnHand sólo se usa al crear; después se modifica con stock-adjustments (TC-13).
 * - version (opcional) permite detectar una edición concurrente (409).
 */
@Data
public class IngredientRequest {
    private String id;

    @NotBlank
    @Size(max = 50)
    private String name;

    @NotNull
    private Type type;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal unitPrice;

    private boolean available;

    @Min(0)
    private int stockOnHand;

    @Min(0)
    private int reorderLevel;

    private Long version;
}
