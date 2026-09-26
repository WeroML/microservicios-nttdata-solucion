package tacos.kitchen.messaging.rabbit.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tacos.kitchen.KitchenEventProcessor;
import tacos.messaging.contract.OrderEvent;

/**
 * TC-30: el ack (modo AUTO) ocurre sólo cuando process() terminó sin error, es decir,
 * después del efecto durable. Los reintentos y la DLQ los configura RabbitKitchenConfig.
 */
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
@Component
public class OrderListener {

  private final KitchenEventProcessor processor;

  public OrderListener(KitchenEventProcessor processor) {
    this.processor = processor;
  }

  @RabbitListener(queues = "${tacocloud.messaging.rabbit.queue}", containerFactory = "rabbitListenerContainerFactory")
  public void receiveOrder(OrderEvent event) {
    processor.process(event);
  }
}
