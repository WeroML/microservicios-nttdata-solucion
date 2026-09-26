package tacos.messaging;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// TC-28: nombre único (antes MessagingConfig, que colisionaba por FQCN con el de JMS).
@Configuration
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
public class RabbitMessagingConfig {

  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

}
