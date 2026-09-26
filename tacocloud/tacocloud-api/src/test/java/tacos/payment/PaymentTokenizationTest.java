package tacos.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.PaymentMethod;
import tacos.api.dto.PaymentTokenizeRequest;
import tacos.data.PaymentMethodRepository;
import tacos.testsupport.Fixtures;
import tacos.web.api.PaymentMethodController;

// TC-12: tokenización con datos sintéticos; nunca se guarda ni expone PAN, CVV o token.
class PaymentTokenizationTest {

    private static final String SYNTHETIC_VISA = "4111111111111111";

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private static PaymentTokenizeRequest request() {
        PaymentTokenizeRequest request = new PaymentTokenizeRequest();
        request.setCardNumber(SYNTHETIC_VISA);
        request.setExpiration("10/30");
        request.setCvv("123");
        return request;
    }

    @Test
    void fakeGatewayTokenizesSyntheticCard() {
        StepVerifier.create(new FakePaymentGateway().tokenize(SYNTHETIC_VISA, "10/30", "123"))
            .assertNext(card -> {
                assertThat(card.getToken()).startsWith("tok_").doesNotContain(SYNTHETIC_VISA);
                assertThat(card.getBrand()).isEqualTo("VISA");
                assertThat(card.getLast4()).isEqualTo("1111");
            })
            .verifyComplete();
    }

    @Test
    void persistedPaymentMethodHasNoPanNorCvvAndResponseHidesToken() throws Exception {
        PaymentMethodRepository repo = mock(PaymentMethodRepository.class);
        when(repo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        PaymentMethodController controller = new PaymentMethodController(new FakePaymentGateway(), repo);

        String json = mapper.writeValueAsString(controller.tokenize(request(),
            Fixtures.authentication(Fixtures.USER_ID, "USER")).block());

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        org.mockito.Mockito.verify(repo).save(saved.capture());
        String persisted = mapper.writeValueAsString(saved.getValue());
        assertThat(persisted).doesNotContain(SYNTHETIC_VISA, "\"123\"", "cvv", "ccNumber");
        assertThat(saved.getValue().getUserId()).isEqualTo(Fixtures.USER_ID);

        assertThat(json).contains("\"brand\":\"VISA\"", "\"last4\":\"1111\"");
        assertThat(json).doesNotContain("tok_", SYNTHETIC_VISA, "123\"");
    }

    @Test
    void requestNeverPrintsCardData() {
        assertThat(request().toString()).doesNotContain(SYNTHETIC_VISA, "123");
    }

    @Test
    void domainAndEventsHaveNoCardFields() {
        assertThat(tacos.TacoOrder.class.getDeclaredFields()).extracting("name")
            .doesNotContain("ccNumber", "ccCVV", "ccExpiration");
        assertThat(PaymentMethod.class.getDeclaredFields()).extracting("name")
            .doesNotContain("ccNumber", "ccCVV");
        assertThat(tacos.messaging.contract.OrderEventPayload.class.getDeclaredFields()).extracting("name")
            .doesNotContain("paymentMethodId", "paymentToken", "paymentLast4", "ccNumber");
    }
}
