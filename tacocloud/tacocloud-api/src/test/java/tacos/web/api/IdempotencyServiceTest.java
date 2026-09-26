package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import tacos.api.dto.OrderCreateRequest;
import tacos.api.error.BadRequestException;
import tacos.testsupport.Fixtures;

// TC-34: hash canónico y formato de la llave (el flujo con Mongo real está en la suite de integración).
class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService(mock(IdempotencyRecordRepository.class),
        mock(ReactiveMongoTemplate.class), new IdempotencyProperties(), Fixtures.CLOCK);

    @Test
    void sameRelevantContentProducesSameHash() {
        OrderCreateRequest a = Fixtures.simpleOrderRequest();
        OrderCreateRequest b = Fixtures.simpleOrderRequest();
        b.setDeliveryName("  Craig ");
        a.setDiscountCode("abcdef");
        b.setDiscountCode("ABCDEF");

        assertThat(service.hash(a)).isEqualTo(service.hash(b));
    }

    @Test
    void differentAddressQuantityOrPaymentChangesTheHash() {
        String base = service.hash(Fixtures.simpleOrderRequest());

        OrderCreateRequest otherZip = Fixtures.simpleOrderRequest();
        otherZip.setDeliveryZip("99999");
        OrderCreateRequest otherQty = Fixtures.simpleOrderRequest();
        otherQty.getItems().get(0).setQuantity(2);
        OrderCreateRequest otherPayment = Fixtures.simpleOrderRequest();
        otherPayment.setPaymentMethodId("pm-z");

        assertThat(service.hash(otherZip)).isNotEqualTo(base);
        assertThat(service.hash(otherQty)).isNotEqualTo(base);
        assertThat(service.hash(otherPayment)).isNotEqualTo(base);
    }

    @Test
    void keyFormatIsValidated() {
        IdempotencyService.validateKey("abc-123_XYZ");
        assertThatThrownBy(() -> IdempotencyService.validateKey("short")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> IdempotencyService.validateKey("bad key with spaces"))
            .isInstanceOf(BadRequestException.class);
    }
}
