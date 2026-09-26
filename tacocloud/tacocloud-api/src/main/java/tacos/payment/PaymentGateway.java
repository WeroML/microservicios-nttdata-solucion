package tacos.payment;

import reactor.core.publisher.Mono;

/**
 * TC-12: puerto hacia el gateway de pagos. Recibe los datos de tarjeta una sola
 * vez y devuelve un token; la aplicación nunca guarda PAN ni CVV.
 */
public interface PaymentGateway {

    Mono<TokenizedCard> tokenize(String cardNumber, String expiration, String cvv);

    final class TokenizedCard {
        private final String token;
        private final String brand;
        private final String last4;

        public TokenizedCard(String token, String brand, String last4) {
            this.token = token;
            this.brand = brand;
            this.last4 = last4;
        }

        public String getToken() {
            return token;
        }

        public String getBrand() {
            return brand;
        }

        public String getLast4() {
            return last4;
        }
    }
}
