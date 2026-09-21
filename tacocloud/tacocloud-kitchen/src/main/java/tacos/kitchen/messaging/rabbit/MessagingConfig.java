package tacos.kitchen.messaging.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
@Configuration
public class MessagingConfig {

  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public Queue orderQueue() {
      return QueueBuilder.durable("tacocloud.order.queue")
          .withArgument("x-dead-letter-exchange", "")
          .withArgument("x-dead-letter-routing-key", "tacocloud.order.dlq")
          .build();
  }

  @Bean
  public Queue dlq() {
      return QueueBuilder.durable("tacocloud.order.dlq").build();
  }
}
