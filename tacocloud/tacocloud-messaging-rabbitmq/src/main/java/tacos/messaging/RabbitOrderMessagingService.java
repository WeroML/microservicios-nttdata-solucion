package tacos.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
public class RabbitOrderMessagingService implements OrderMessagingService {

  private RabbitTemplate rabbit;

  public RabbitOrderMessagingService(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    rabbit.convertAndSend("tacocloud.order.queue", event, 
        message -> {
          MessageProperties props = message.getMessageProperties();
          props.setHeader("X_ORDER_SOURCE", "WEB");
          return message;
        });
  }

}
