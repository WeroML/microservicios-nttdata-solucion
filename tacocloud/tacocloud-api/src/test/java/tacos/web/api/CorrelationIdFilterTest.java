package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.OrderItem;
import tacos.OrderStatus;
import tacos.OutboxEvent;
import tacos.TacoOrder;
import tacos.data.OrderRepository;
import tacos.data.OutboxEventRepository;
import tacos.messaging.contract.OrderEvent;
import tacos.testsupport.Fixtures;

// TC-31: X-Correlation-Id de HTTP a evento y logs.
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String header, AtomicReference<String> seenInMdc) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tacos");
        if (header != null) {
            request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> seenInMdc.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY));
        filter.doFilter(request, response, chain);
        return response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    }

    @Test
    void generatesAUuidWhenMissing() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();
        String id = run(null, mdc);

        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(mdc.get()).isEqualTo(id);
    }

    @Test
    void preservesAValidHeader() throws Exception {
        assertThat(run("order-flow-42", new AtomicReference<>())).isEqualTo("order-flow-42");
    }

    @Test
    void replacesMaliciousOrTooLongValues() throws Exception {
        String injected = run("abc\nFAKE LOG LINE", new AtomicReference<>());
        String tooLong = run(new String(new char[65]).replace('\0', 'a'), new AtomicReference<>());

        assertThat(injected).doesNotContain("\n", "FAKE");
        assertThat(UUID.fromString(injected)).isNotNull();
        assertThat(UUID.fromString(tooLong)).isNotNull();
    }

    @Test
    void mdcIsCleanAfterEachRequest() throws Exception {
        run("first-request", new AtomicReference<>());
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)).isNull();

        AtomicReference<String> second = new AtomicReference<>();
        run(null, second);
        assertThat(second.get()).isNotEqualTo("first-request");
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)).isNull();
    }

    // MDC no viaja por Reactor: el ID se copia al Context y la fábrica de eventos lo usa.
    @Test
    void correlationIdReachesTheOutboxEventThroughReactorContext() {
        OrderRepository orderRepo = mock(OrderRepository.class);
        OutboxEventRepository outboxRepo = mock(OutboxEventRepository.class);
        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        AtomicReference<OutboxEvent> saved = new AtomicReference<>();
        when(outboxRepo.save(any())).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return Mono.just(inv.getArgument(0));
        });
        TransactionalOrderService service = new TransactionalOrderService(orderRepo, outboxRepo,
            new OrderEventFactory(Fixtures.CLOCK), mock(IdempotencyService.class), Fixtures.CLOCK);
        TacoOrder order = new TacoOrder();
        order.setId("o1");
        order.setStatus(OrderStatus.CREATED);
        OrderItem item = new OrderItem();
        item.setTaco(Fixtures.taco(null, "Classic", Fixtures.catalog(), "FLTO", "GRBF"));
        item.setQuantity(1);
        order.addItem(item);

        MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, "cid-from-http");
        reactor.util.context.Context context = CorrelationIdFilter.reactorContext();
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);

        StepVerifier.create(service.saveNewOrder(order, null).contextWrite(context))
            .expectNextCount(1).verifyComplete();
        OrderEvent event = (OrderEvent) saved.get().getPayload();
        assertThat(event.getCorrelationId()).isEqualTo("cid-from-http");
        assertThat(event.getCorrelationId()).isNotEqualTo(order.getId());
    }
}
