package tacos.api.dto;

import javax.validation.constraints.NotBlank;

import lombok.Data;

// TC-24: el método de pago se elige de nuevo; nunca se copia el de la orden original.
@Data
public class ReorderRequest {
    @NotBlank
    private String paymentMethodId;
    private boolean confirmPriceChange;
}
