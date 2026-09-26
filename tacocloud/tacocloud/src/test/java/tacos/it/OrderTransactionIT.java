package tacos.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import reactor.core.publisher.Mono;
import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.data.OutboxEventRepository;

/**
 * TC-29: orden y outbox se guardan en la misma transacción de Mongo (replica set).
 * Si registrar el evento falla, tampoco queda la orden, y el inventario se libera.
 */
class OrderTransactionIT extends IntegrationTestBase {

    @SpyBean
    private OutboxEventRepository outboxRepo;

    @Test
    void failureBeforeCommitLeavesNoOrderAndNoOutbox() {
        doReturn(Mono.error(new IllegalStateException("outbox write failed"))).when(outboxRepo).save(any());
        String[] customer = newCustomer();

        postOrder(customer[0], orderJson(customer[1], 2, "FLTO", "GRBF"), null)
            .expectStatus().is5xxServerError()
            .expectBody().jsonPath("$.code").isEqualTo("INTERNAL_ERROR");

        assertThat(count(TacoOrder.class)).isZero();
        assertThat(count(OutboxEvent.class)).isZero();
        assertThat(stockOf("GRBF")).isEqualTo(100);
    }
}
