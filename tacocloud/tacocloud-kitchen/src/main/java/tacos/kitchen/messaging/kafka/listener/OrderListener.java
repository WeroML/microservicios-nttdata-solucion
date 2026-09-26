package tacos.kitchen.messaging.kafka.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tacos.kitchen.KitchenEventProcessor;
import tacos.messaging.contract.OrderEvent;

@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="kafka")
@Component
public class OrderListener {

  private final KitchenEventProcessor processor;

  public OrderListener(KitchenEventProcessor processor) {
    this.processor = processor;
  }

  @KafkaListener(topics = "${tacocloud.messaging.kafka.topic}")
  public void handle(OrderEvent event) {
    processor.process(event);
  }
}
