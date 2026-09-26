package tacos.kitchen.messaging.rabbit;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * TC-30 (broker de extremo a extremo: RabbitMQ).
 * - Sólo se reintentan errores transitorios (fallas de acceso a datos), con
 *   maxAttempts y backoff exponencial configurables.
 * - Errores permanentes (validación, versión desconocida, JSON inválido) no se
 *   reintentan: van directo a la DLQ, sin loop infinito.
 * - Cola principal y DLQ específica se declaran con nombres externos.
 */
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
@Configuration
public class RabbitKitchenConfig {

  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public Queue orderQueue(@Value("${tacocloud.messaging.rabbit.queue}") String queue,
                          @Value("${tacocloud.messaging.rabbit.dead-letter-queue}") String dlq) {
    return QueueBuilder.durable(queue)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", dlq)
        .build();
  }

  @Bean
  public Queue deadLetterQueue(@Value("${tacocloud.messaging.rabbit.dead-letter-queue}") String dlq) {
    return QueueBuilder.durable(dlq).build();
  }

  public static SimpleRetryPolicy retryPolicy(int maxAttempts) {
    Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
    retryable.put(TransientDataAccessException.class, true);
    retryable.put(DataAccessResourceFailureException.class, true);
    return new SimpleRetryPolicy(maxAttempts, retryable, true, false);
  }

  public static ExponentialBackOffPolicy backOffPolicy(KitchenRetryProperties props) {
    ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(props.getInitialInterval().toMillis());
    backOff.setMultiplier(props.getMultiplier());
    backOff.setMaxInterval(props.getMaxInterval().toMillis());
    return backOff;
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
      RabbitTemplate rabbitTemplate, KitchenRetryProperties retryProps, MeterRegistry meterRegistry,
      @Value("${tacocloud.messaging.rabbit.dead-letter-queue}") String dlq) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);

    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(retryPolicy(retryProps.getMaxAttempts()));
    retryTemplate.setBackOffPolicy(backOffPolicy(retryProps));

    factory.setAdviceChain(RetryInterceptorBuilder.stateless()
        .retryOperations(retryTemplate)
        .recoverer(new DeadLetterRecoverer(rabbitTemplate, dlq, meterRegistry))
        .build());
    return factory;
  }
}
