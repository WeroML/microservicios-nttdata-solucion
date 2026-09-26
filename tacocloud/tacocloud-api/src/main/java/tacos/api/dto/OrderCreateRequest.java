package tacos.api.dto;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Data;

/**
 * TC-08/TC-14: request de creación de orden.
 * No contiene campos server-owned: id, userId, placedAt, status, precios ni total.
 * Si el cliente los envía, Jackson los ignora y no alteran el dominio.
 */
@Data
public class OrderCreateRequest {
    @NotBlank
    @Size(max = 50)
    private String deliveryName;
    @NotBlank
    @Size(max = 100)
    private String deliveryStreet;
    @NotBlank
    @Size(max = 50)
    private String deliveryCity;
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter state code")
    private String deliveryState;
    @NotBlank
    @Pattern(regexp = "\\d{5}", message = "must be a 5-digit zip code")
    private String deliveryZip;

    // TC-12: sólo el ID de un método tokenizado del propio usuario.
    @NotBlank
    private String paymentMethodId;

    @Size(max = 20)
    private String discountCode;

    @NotEmpty
    @Size(max = 20)
    private List<@Valid @NotNull OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        @NotNull
        @Valid
        private TacoRequest taco;

        // El máximo es configurable (tacocloud.pricing.max-quantity-per-line) y se valida en PricingService.
        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
