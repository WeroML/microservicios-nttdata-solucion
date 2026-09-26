package tacos.discount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * TC-15: motor de cupones PERCENTAGE y FIXED con vigencia, mínimo de compra y descuento máximo.
 * - Los códigos se normalizan (trim + mayúsculas): la comparación es case-insensitive.
 * - La vigencia es inclusiva en ambos extremos y se evalúa con el Clock inyectado.
 * - El descuento nunca vuelve negativo el total.
 */
@Service
public class DiscountService {

    private final DiscountProperties properties;
    private final Clock clock;

    public DiscountService(DiscountProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public DiscountResult applyDiscount(String code, BigDecimal subtotal) {
        String normalizedCode = normalize(code);
        DiscountProperties.Coupon coupon = findCoupon(normalizedCode);
        if (coupon == null) {
            return DiscountResult.rejected(Status.UNKNOWN_CODE, subtotal);
        }

        LocalDate today = LocalDate.now(clock);
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            return DiscountResult.rejected(Status.NOT_YET_VALID, subtotal);
        }
        if (coupon.getValidUntil() != null && today.isAfter(coupon.getValidUntil())) {
            return DiscountResult.rejected(Status.EXPIRED, subtotal);
        }
        if (coupon.getMinPurchase() != null && subtotal.compareTo(coupon.getMinPurchase()) < 0) {
            return DiscountResult.rejected(Status.MIN_PURCHASE_NOT_MET, subtotal);
        }

        BigDecimal discount;
        if (coupon.getType() == DiscountProperties.Type.FIXED) {
            discount = coupon.getAmount();
        } else {
            discount = subtotal.multiply(coupon.getAmount());
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        }
        discount = discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new DiscountResult(Status.APPLIED, normalizedCode, discount, total);
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private DiscountProperties.Coupon findCoupon(String normalizedCode) {
        for (Map.Entry<String, DiscountProperties.Coupon> entry : properties.getCodes().entrySet()) {
            if (normalize(entry.getKey()).equals(normalizedCode)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public enum Status {
        APPLIED, UNKNOWN_CODE, NOT_YET_VALID, EXPIRED, MIN_PURCHASE_NOT_MET
    }

    public static class DiscountResult {
        public final Status status;
        public final String appliedCode;
        public final BigDecimal discount;
        public final BigDecimal total;

        public DiscountResult(Status status, String appliedCode, BigDecimal discount, BigDecimal total) {
            this.status = status;
            this.appliedCode = appliedCode;
            this.discount = discount;
            this.total = total;
        }

        static DiscountResult rejected(Status status, BigDecimal subtotal) {
            return new DiscountResult(status, null, BigDecimal.ZERO.setScale(2), subtotal);
        }

        public boolean isApplied() {
            return status == Status.APPLIED;
        }

        // Código público que no revela si el cupón existe (ver CouponValidationResponse).
        public String publicResult() {
            switch (status) {
                case APPLIED:
                    return "COUPON_APPLIED";
                case MIN_PURCHASE_NOT_MET:
                    return "COUPON_MIN_PURCHASE_NOT_MET";
                default:
                    return "COUPON_NOT_APPLICABLE";
            }
        }
    }
}
