package tacos.discount;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tacos.testsupport.Fixtures;

// TC-15: motor de cupones con Clock inyectado.
class DiscountServiceTest {

    private final DiscountService service = Fixtures.discountService(Fixtures.CLOCK);

    @ParameterizedTest(name = "{0} sobre {1} -> {2}")
    @CsvSource({
        "abcdef, 10.00, APPLIED, 1.00, 9.00",
        "ABCDEF, 80.00, APPLIED, 5.00, 75.00",      // tope maxDiscount
        "HALF, 10.00, APPLIED, 3.00, 7.00",         // 50% limitado a 3.00
        "TACO2, 12.00, APPLIED, 2.00, 10.00",       // FIXED
        "TACO2, 4.99, MIN_PURCHASE_NOT_MET, 0.00, 4.99",
        "OLD, 10.00, EXPIRED, 0.00, 10.00",
        "FUTURE, 10.00, NOT_YET_VALID, 0.00, 10.00",
        "NOPE, 10.00, UNKNOWN_CODE, 0.00, 10.00",
        "BIGFIX, 3.50, APPLIED, 3.50, 0.00"         // nunca negativo
    })
    void couponRules(String code, String subtotal, String status, String discount, String total) {
        DiscountService.DiscountResult result = service.applyDiscount(code, new BigDecimal(subtotal));

        assertThat(result.status.name()).isEqualTo(status);
        assertThat(result.discount).isEqualByComparingTo(discount);
        assertThat(result.total).isEqualByComparingTo(total);
    }

    @Test
    void codeIsCaseInsensitiveAndTrimmed() {
        assertThat(service.applyDiscount("  AbCdEf ", new BigDecimal("10")).appliedCode).isEqualTo("ABCDEF");
    }

    @Test
    void validityIsInclusiveAtTheDayBoundaryOfTheConfiguredZone() {
        ZoneId zone = ZoneId.of("America/Mexico_City");
        // 2026-12-31 23:59 en México (ya es 2027-01-01 en UTC): todavía vigente.
        Clock lastMinute = Clock.fixed(Instant.parse("2027-01-01T05:59:00Z"), zone);
        Clock nextDay = Clock.fixed(Instant.parse("2027-01-01T06:00:00Z"), zone);

        assertThat(Fixtures.discountService(lastMinute).applyDiscount("abcdef", BigDecimal.TEN).isApplied()).isTrue();
        assertThat(Fixtures.discountService(nextDay).applyDiscount("abcdef", BigDecimal.TEN).status)
            .isEqualTo(DiscountService.Status.EXPIRED);
    }

    // El endpoint no permite enumerar: desconocido, expirado y no iniciado responden igual.
    @Test
    void publicResultDoesNotRevealWhetherACouponExists() {
        String unknown = service.applyDiscount("NOPE", BigDecimal.TEN).publicResult();
        String expired = service.applyDiscount("OLD", BigDecimal.TEN).publicResult();
        String future = service.applyDiscount("FUTURE", BigDecimal.TEN).publicResult();

        assertThat(unknown).isEqualTo(expired).isEqualTo(future).isEqualTo("COUPON_NOT_APPLICABLE");
    }
}
