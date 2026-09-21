package tacos.discount;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class DiscountService {

    private final DiscountProperties properties;
    private final Clock clock;

    public DiscountService(DiscountProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public DiscountResult applyDiscount(String code, BigDecimal subtotal) {
        if (code == null || code.trim().isEmpty()) {
            return new DiscountResult(subtotal, BigDecimal.ZERO, "NO_CODE", "No discount code provided.");
        }

        String normalizedCode = code.trim().toUpperCase();
        DiscountProperties.Coupon coupon = properties.getCodes().get(normalizedCode);

        if (coupon == null) {
            return new DiscountResult(subtotal, BigDecimal.ZERO, "UNKNOWN_CODE", "Discount code not found.");
        }

        LocalDate today = LocalDate.now(clock);
        if (coupon.getValidFrom() != null && today.isBefore(coupon.getValidFrom())) {
            return new DiscountResult(subtotal, BigDecimal.ZERO, "NOT_YET_VALID", "Discount code is not valid yet.");
        }
        if (coupon.getValidUntil() != null && today.isAfter(coupon.getValidUntil())) {
            return new DiscountResult(subtotal, BigDecimal.ZERO, "EXPIRED", "Discount code has expired.");
        }

        if (coupon.getMinPurchase() != null && subtotal.compareTo(coupon.getMinPurchase()) < 0) {
            return new DiscountResult(subtotal, BigDecimal.ZERO, "MIN_PURCHASE_NOT_MET", "Minimum purchase amount not met.");
        }

        BigDecimal discountAmount = BigDecimal.ZERO;

        if (coupon.getType() == DiscountProperties.Type.FIXED) {
            discountAmount = coupon.getAmount();
        } else if (coupon.getType() == DiscountProperties.Type.PERCENTAGE) {
            discountAmount = subtotal.multiply(coupon.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && discountAmount.compareTo(coupon.getMaxDiscount()) > 0) {
                discountAmount = coupon.getMaxDiscount();
            }
        }

        BigDecimal newTotal = subtotal.subtract(discountAmount);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
            discountAmount = subtotal; // discount cannot exceed subtotal
            newTotal = BigDecimal.ZERO;
        }

        return new DiscountResult(newTotal, discountAmount, "APPLIED", "Discount applied successfully.", normalizedCode);
    }

    public static class DiscountResult {
        public final BigDecimal finalTotal;
        public final BigDecimal discountApplied;
        public final String status;
        public final String message;
        public final String appliedCode;

        public DiscountResult(BigDecimal finalTotal, BigDecimal discountApplied, String status, String message) {
            this(finalTotal, discountApplied, status, message, null);
        }

        public DiscountResult(BigDecimal finalTotal, BigDecimal discountApplied, String status, String message, String appliedCode) {
            this.finalTotal = finalTotal;
            this.discountApplied = discountApplied;
            this.status = status;
            this.message = message;
            this.appliedCode = appliedCode;
        }
    }
}
