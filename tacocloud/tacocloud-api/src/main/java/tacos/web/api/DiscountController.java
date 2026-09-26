package tacos.web.api;

import javax.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.CouponValidationRequest;
import tacos.api.dto.CouponValidationResponse;
import tacos.discount.DiscountService;

// TC-15: valida un cupón sin crear la orden. No existe endpoint que liste cupones.
@RestController
@RequestMapping(path={"/api/v1/coupons", "/api/coupons"}, produces="application/json")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @PostMapping(path="/validate", consumes="application/json")
    public Mono<CouponValidationResponse> validateCoupon(@Valid @RequestBody CouponValidationRequest request) {
        return Mono.fromSupplier(() -> {
            DiscountService.DiscountResult result = discountService.applyDiscount(request.getCode(), request.getSubtotal());
            return new CouponValidationResponse(result.isApplied(), result.publicResult(),
                result.discount, result.total);
        });
    }
}
