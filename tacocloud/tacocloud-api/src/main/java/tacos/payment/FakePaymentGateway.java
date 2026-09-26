package tacos.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * TC-12: adaptador fake para el laboratorio. Sólo usar tarjetas sintéticas de
 * prueba (p. ej. 4111111111111111). El CVV se recibe pero no se guarda, no se
 * reenvía y no se registra.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public Mono<TokenizedCard> tokenize(String cardNumber, String expiration, String cvv) {
        return Mono.fromSupplier(() -> new TokenizedCard(
            "tok_" + UUID.randomUUID(),
            brandOf(cardNumber),
            cardNumber.substring(cardNumber.length() - 4)));
    }

    static String brandOf(String cardNumber) {
        if (cardNumber.startsWith("4")) {
            return "VISA";
        }
        if (cardNumber.startsWith("5")) {
            return "MASTERCARD";
        }
        if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) {
            return "AMEX";
        }
        return "OTHER";
    }
}
