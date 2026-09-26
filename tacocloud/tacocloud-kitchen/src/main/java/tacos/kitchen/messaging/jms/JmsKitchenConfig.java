package tacos.kitchen.messaging.jms;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;

import tacos.messaging.contract.OrderEvent;

@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="jms")
@Configuration
public class JmsKitchenConfig {

  @Bean
  public MappingJackson2MessageConverter messageConverter() {
    MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
    messageConverter.setTypeIdPropertyName("_typeId");
    Map<String, Class<?>> typeIdMappings = new HashMap<String, Class<?>>();
    typeIdMappings.put("orderEvent", OrderEvent.class);
    messageConverter.setTypeIdMappings(typeIdMappings);
    return messageConverter;
  }

}
