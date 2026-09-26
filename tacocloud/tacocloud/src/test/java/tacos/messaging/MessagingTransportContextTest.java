package tacos.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import tacos.messaging.contract.OrderMessagingService;

/**
 * TC-28: un bean del puerto por transporte, error claro con valores inválidos.
 * Los templates son mocks: ninguna prueba se conecta a un broker.
 */
class MessagingTransportContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(MessagingTransportConfig.class,
            NoOpOrderMessagingService.class, JmsOrderMessagingService.class, JmsMessagingConfig.class,
            RabbitOrderMessagingService.class, RabbitMessagingConfig.class, KafkaOrderMessagingService.class)
        .withBean(JmsTemplate.class, () -> mock(JmsTemplate.class))
        .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .withPropertyValues(
            "tacocloud.messaging.jms.destination=test.queue",
            "tacocloud.messaging.rabbit.routing-key=test.queue",
            "tacocloud.messaging.kafka.topic=test.topic");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "noop, NoOpOrderMessagingService",
        "jms, JmsOrderMessagingService",
        "rabbit, RabbitOrderMessagingService",
        "kafka, KafkaOrderMessagingService"
    })
    void eachTransportCreatesExactlyOnePortBean(String transport, String expectedClass) {
        runner.withPropertyValues("tacocloud.messaging.transport=" + transport).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OrderMessagingService.class);
            assertThat(context.getBean(OrderMessagingService.class).getClass().getSimpleName()).isEqualTo(expectedClass);
        });
    }

    @Test
    void unknownTransportFailsWithAClearMessage() {
        runner.withPropertyValues("tacocloud.messaging.transport=rabbitmq").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessage(
                "tacocloud.messaging.transport must be one of [noop, jms, rabbit, kafka] but was 'rabbitmq'");
        });
    }

    // Sin propiedad no hay default silencioso (en prod no se define): la app no arranca.
    @Test
    void missingTransportIsNotSilentlyNoop() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void twoActiveAdaptersFailAtStartup() {
        runner.withPropertyValues("tacocloud.messaging.transport=noop")
            .withBean("extraAdapter", OrderMessagingService.class, () -> event -> { })
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessage(
                    "Exactly one OrderMessagingService must be active for tacocloud.messaging.transport=noop"
                        + " but found [noOpOrderMessagingService, extraAdapter]");
            });
    }

    @Test
    void noSecretsOrInsecureDefaultsInConfiguration() throws Exception {
        String yml = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/main/resources/application.yml")));
        assertThat(yml).doesNotContain("letm31n", "l3tm31n", "53cr3t", "tacopassword");
        assertThat(yml).contains("password: ${RABBITMQ_PASSWORD:}", "password: ${ARTEMIS_PASSWORD:}");
    }
}
