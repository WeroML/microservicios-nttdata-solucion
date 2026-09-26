package tacos.kitchen.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tacos.kitchen.UnsupportedEventException;

// TC-30: reintentos limitados sólo para errores transitorios, backoff virtual y DLQ con causa y correlación.
class RetryAndDeadLetterTest {

    private final List<Long> sleeps = new ArrayList<>();

    private RetryTemplate retryTemplate(int maxAttempts) {
        KitchenRetryProperties props = new KitchenRetryProperties();
        props.setMaxAttempts(maxAttempts);
        props.setInitialInterval(Duration.ofMillis(100));
        props.setMultiplier(2.0);
        props.setMaxInterval(Duration.ofMillis(300));
        ExponentialBackOffPolicy backOff = RabbitKitchenConfig.backOffPolicy(props);
        backOff.setSleeper(sleeps::add); // backoff virtual: no hay sleeps reales
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(RabbitKitchenConfig.retryPolicy(props.getMaxAttempts()));
        template.setBackOffPolicy(backOff);
        return template;
    }

    @Test
    void transientErrorIsRetriedTheConfiguredNumberOfTimesWithBackoff() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryTemplate(4).execute(ctx -> {
            attempts.incrementAndGet();
            throw new DataAccessResourceFailureException("mongo unavailable");
        })).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(attempts.get()).isEqualTo(4);
        assertThat(sleeps).containsExactly(100L, 200L, 300L);
    }

    @Test
    void permanentErrorsAreNotRetried() {
        for (RuntimeException permanent : new RuntimeException[] {
                new UnsupportedEventException("Unsupported event version: v9"),
                new MessageConversionException("bad json")}) {
            AtomicInteger attempts = new AtomicInteger();
            assertThatThrownBy(() -> retryTemplate(4).execute(ctx -> {
                attempts.incrementAndGet();
                throw permanent;
            })).isSameAs(permanent);
            assertThat(attempts.get()).isEqualTo(1);
        }
    }

    @Test
    void exhaustedMessageGoesToTheDlqWithCauseAndCorrelation() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeadLetterRecoverer recoverer = new DeadLetterRecoverer(rabbit, "orders.dlq", registry);
        MessageProperties props = new MessageProperties();
        props.setHeader("X-Correlation-Id", "cid-77");
        props.setConsumerQueue("orders");
        Message message = new Message("{\"eventId\":\"e-1\"}".getBytes(), props);

        recoverer.recover(message, new RuntimeException("listener failed",
            new UnsupportedEventException("Unsupported event version: v9")));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(""), eq("orders.dlq"), sent.capture());
        MessageProperties headers = sent.getValue().getMessageProperties();
        assertThat((String) headers.getHeader(DeadLetterRecoverer.CAUSE_TYPE_HEADER))
            .isEqualTo(UnsupportedEventException.class.getName());
        assertThat((String) headers.getHeader(DeadLetterRecoverer.CAUSE_MESSAGE_HEADER)).contains("v9");
        assertThat((String) headers.getHeader("X-Correlation-Id")).isEqualTo("cid-77");
        assertThat((String) headers.getHeader(DeadLetterRecoverer.ORIGINAL_QUEUE_HEADER)).isEqualTo("orders");
        assertThat(headers.getHeaders()).doesNotContainKey("x-exception-stacktrace");
        assertThat(registry.counter("tacocloud.kitchen.events", "result", "dlq").count()).isEqualTo(1.0);
    }
}
