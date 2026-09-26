package tacos.web.api;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.PaymentMethod;
import tacos.api.dto.PaymentMethodResponse;
import tacos.api.dto.PaymentTokenizeRequest;
import tacos.data.PaymentMethodRepository;
import tacos.payment.PaymentGateway;

// TC-12: tokenización simulada. La respuesta sólo expone brand/last4; nunca el token ni el PAN.
@RestController
@RequestMapping(path={"/api/v1/payment-methods", "/api/payment-methods"}, produces="application/json")
public class PaymentMethodController {

    private final PaymentGateway gateway;
    private final PaymentMethodRepository paymentMethodRepo;

    public PaymentMethodController(PaymentGateway gateway, PaymentMethodRepository paymentMethodRepo) {
        this.gateway = gateway;
        this.paymentMethodRepo = paymentMethodRepo;
    }

    @PostMapping(path="/tokenize", consumes="application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PaymentMethodResponse> tokenize(@Valid @RequestBody PaymentTokenizeRequest request,
                                                Authentication authentication) {
        String userId = Actor.from(authentication).getUserId();
        return gateway.tokenize(request.getCardNumber(), request.getExpiration(), request.getCvv())
            .flatMap(card -> paymentMethodRepo.save(new PaymentMethod(userId, card.getToken(),
                card.getBrand(), card.getLast4(), request.getExpiration())))
            .map(PaymentMethodController::toResponse);
    }

    @GetMapping
    public Flux<PaymentMethodResponse> myPaymentMethods(Authentication authentication) {
        return paymentMethodRepo.findByUserId(Actor.from(authentication).getUserId())
            .map(PaymentMethodController::toResponse);
    }

    static PaymentMethodResponse toResponse(PaymentMethod pm) {
        return new PaymentMethodResponse(pm.getId(), pm.getBrand(), pm.getLast4(), pm.getExpiration());
    }
}
