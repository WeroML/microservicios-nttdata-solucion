package tacos.api.dto;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class CouponValidationRequest {
    @NotBlank
    @Size(max = 20)
    private String code;
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal subtotal;
}
