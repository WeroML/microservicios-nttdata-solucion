package tacos.discount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import lombok.Data;

// TC-15: los cupones vienen de configuración (YAML/variables), no del código.
@Component
@ConfigurationProperties(prefix="taco.discount")
@Data
public class DiscountProperties {

    private Map<String, Coupon> codes = new HashMap<>();

    @Data
    public static class Coupon {
        private Type type;
        private BigDecimal amount; // PERCENTAGE: 0.10 = 10%; FIXED: monto en la moneda
        @DateTimeFormat(pattern="yyyy-MM-dd")
        private LocalDate validFrom;
        @DateTimeFormat(pattern="yyyy-MM-dd")
        private LocalDate validUntil;
        private BigDecimal minPurchase = BigDecimal.ZERO;
        private BigDecimal maxDiscount;
    }

    public enum Type {
        PERCENTAGE, FIXED
    }
}
