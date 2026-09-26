package tacos.api.dto;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

import lombok.Data;

/**
 * TC-14/TC-17/TC-18: diseño de taco enviado por el cliente.
 * Sólo nombre e IDs de ingredientes: precios, etiquetas, alérgenos y picante
 * se resuelven en el servidor a partir del catálogo.
 */
@Data
public class TacoRequest {
    @NotBlank
    @Size(min = 5, max = 50)
    private String name;

    @NotEmpty
    private List<@NotBlank String> ingredientIds;
}
