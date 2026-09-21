package tacos.web.api;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;
import lombok.Data;

@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentGatewayController {

    @PostMapping("/tokenize")
    public Mono<TokenResponse> tokenize(@RequestBody TokenizeRequest req) {
        // Fake tokenizer
        String token = "tok_" + UUID.randomUUID().toString();
        String last4 = req.getCcNumber() != null && req.getCcNumber().length() >= 4 
            ? req.getCcNumber().substring(req.getCcNumber().length() - 4) 
            : "0000";
        return Mono.just(new TokenResponse(token, "Visa", last4));
    }

    @Data
    public static class TokenizeRequest {
        private String ccNumber;
        private String ccCVV;
        private String ccExpiration;
    }

    @Data
    public static class TokenResponse {
        private final String token;
        private final String brand;
        private final String last4;
    }
}
