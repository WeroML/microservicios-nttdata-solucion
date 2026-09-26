package tacos.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-12: sólo brand/last4; el token completo no sale del servidor.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {
    private String id;
    private String brand;
    private String last4;
    private String expiration;
}
