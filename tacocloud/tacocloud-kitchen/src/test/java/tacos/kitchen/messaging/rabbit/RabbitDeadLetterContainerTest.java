package tacos.kitchen.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tacos.kitchen.KitchenEventProcessorTest;
import tacos.kitchen.ProcessedEventRepository;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderEventType;

/**
 * TC-30 de extremo a extremo con RabbitMQ real en contenedor (Mongo embebido).
 * Se omite si no hay Docker; en CI corre.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "tacocloud.messaging.transport=rabbit",
    "spring.mongodb.embedded.version=4.0.12",
    "tacocloud.kitchen.retry.initial-interval=10ms",
    "tacocloud.kitchen.retry.max-interval=20ms"
})
class RabbitDeadLetterContainerTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.9-management");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ProcessedEventRepository processedEvents;

    private static Message awaitDlq(RabbitTemplate rabbit) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            Message message = rabbit.receive("tacocloud.order.dlq", 500);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    @Test
    void duplicateDeliveryIsProcessedOnceAndPoisonMessageEndsInDlq() {
        OrderEvent event = KitchenEventProcessorTest.event("e-rabbit-1", OrderEventType.ORDER_CREATED, "CREATED");
        rabbit.convertAndSend("", "tacocloud.order.queue", event);
        rabbit.convertAndSend("", "tacocloud.order.queue", event);

        OrderEvent unsupported = KitchenEventProcessorTest.event("e-rabbit-2", OrderEventType.ORDER_CREATED, "CREATED");
        unsupported.setVersion("v99");
        rabbit.convertAndSend("", "tacocloud.order.queue", unsupported, m -> {
            m.getMessageProperties().setHeader("X-Correlation-Id", "cid-poison");
            return m;
        });

        Message dead = awaitDlq(rabbit);
        assertThat(dead).isNotNull();
        MessageProperties headers = dead.getMessageProperties();
        assertThat((String) headers.getHeader("X-Correlation-Id")).isEqualTo("cid-poison");
        assertThat((String) headers.getHeader(DeadLetterRecoverer.CAUSE_MESSAGE_HEADER)).contains("v99");
        assertThat(processedEvents.existsById("e-rabbit-1")).isTrue();
        assertThat(processedEvents.count()).isEqualTo(1);
    }
}
