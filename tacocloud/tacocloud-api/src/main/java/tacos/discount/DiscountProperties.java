package tacos.discount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="taco.discount")
@Data
public class DiscountProperties {

    private Map<String, Coupon> codes = new HashMap<>();

    @Data
    public static class Coupon {
        private Type type;
        private BigDecimal amount; // Percentage (e.g. 0.20) or Fixed (e.g. 5.00)
        private @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM-dd") LocalDate validFrom;
        private @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM-dd") LocalDate validUntil;
        private BigDecimal minPurchase = BigDecimal.ZERO;
        private BigDecimal maxDiscount; // Max discount allowed for percentage
    }

    public enum Type {
        PERCENTAGE, FIXED
    }
}
