package tacos.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import lombok.Data;

/**
 * TC-12: datos de tarjeta que sólo viajan al gateway (fake en laboratorio).
 * Nunca se guardan, reenvían ni registran. Usar sólo tarjetas sintéticas de prueba.
 */
@Data
public class PaymentTokenizeRequest {
    @NotBlank
    @Pattern(regexp = "\\d{13,19}", message = "must be 13 to 19 digits")
    private String cardNumber;
    @NotBlank
    @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "must be formatted MM/YY")
    private String expiration;
    @NotBlank
    @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits")
    private String cvv;

    // Nunca imprimir número ni CVV.
    @Override
    public String toString() {
        return "PaymentTokenizeRequest(cardNumber=****, cvv=***)";
    }
}
