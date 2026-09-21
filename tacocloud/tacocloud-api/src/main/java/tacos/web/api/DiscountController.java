package tacos.web.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tacos.discount.DiscountService;
import java.math.BigDecimal;
import lombok.Data;

@RestController
@RequestMapping(path="/api/v1/coupons", produces="application/json")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<DiscountService.DiscountResult>> validateCoupon(@RequestBody ValidateCouponRequest request) {
        if (request.getCode() == null || request.getSubtotal() == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        
        DiscountService.DiscountResult result = discountService.applyDiscount(request.getCode(), request.getSubtotal());
        return Mono.just(ResponseEntity.ok(result));
    }

    @Data
    public static class ValidateCouponRequest {
        private String code;
        private BigDecimal subtotal;
    }
}
