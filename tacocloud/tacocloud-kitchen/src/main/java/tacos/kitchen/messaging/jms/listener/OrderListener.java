package tacos.kitchen.messaging.jms.listener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import tacos.kitchen.KitchenEventProcessor;
import tacos.messaging.contract.OrderEvent;

@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="jms")
@Component
public class OrderListener {

  private final KitchenEventProcessor processor;

  public OrderListener(KitchenEventProcessor processor) {
    this.processor = processor;
  }

  @JmsListener(destination = "${tacocloud.messaging.jms.destination}")
  public void receiveOrder(OrderEvent event) {
    processor.process(event);
  }
}
