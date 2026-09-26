package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tacos.messaging.contract.OrderMessagingService;
import tacos.testsupport.Fixtures;

// TC-29: backoff exponencial con tope configurable (la entrega real se prueba con Mongo en la suite de integración).
class OutboxPublisherTest {

    @Test
    void backoffIsExponentialAndCapped() {
        OutboxProperties props = new OutboxProperties();
        props.setInitialBackoff(Duration.ofSeconds(2));
        props.setMaxBackoff(Duration.ofSeconds(30));
        OutboxPublisher publisher = new OutboxPublisher(mock(ReactiveMongoTemplate.class),
            mock(OrderMessagingService.class), props, new TacoMetricsService(new SimpleMeterRegistry()), Fixtures.CLOCK);

        assertThat(publisher.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(publisher.backoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(publisher.backoff(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(publisher.backoff(10)).isEqualTo(Duration.ofSeconds(30));
    }
}
