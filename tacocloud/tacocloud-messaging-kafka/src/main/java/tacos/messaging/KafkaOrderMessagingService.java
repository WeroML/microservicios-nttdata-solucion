package tacos.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="kafka")
public class KafkaOrderMessagingService implements OrderMessagingService {
  
  private KafkaTemplate<String, OrderEvent> kafkaTemplate;

  public KafkaOrderMessagingService(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    kafkaTemplate.send("tacocloud.orders.topic", event);
  }
  
}
