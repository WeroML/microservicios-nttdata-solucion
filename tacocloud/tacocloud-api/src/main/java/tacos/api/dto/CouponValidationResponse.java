package tacos.api.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TC-15: respuesta que no permite enumerar cupones.
 * Código desconocido, expirado y no iniciado responden igual (COUPON_NOT_APPLICABLE);
 * sólo "mínimo no alcanzado" se distingue porque el usuario puede corregirlo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationResponse {
    private boolean valid;
    private String result;
    private BigDecimal discount;
    private BigDecimal total;
}
