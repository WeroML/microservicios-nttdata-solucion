package tacos.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

@Service
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
public class RabbitOrderMessagingService implements OrderMessagingService {

  public static final String CORRELATION_HEADER = "X-Correlation-Id";

  private final RabbitTemplate rabbit;
  private final String exchange;
  private final String routingKey;

  public RabbitOrderMessagingService(RabbitTemplate rabbit,
      @Value("${tacocloud.messaging.rabbit.exchange:}") String exchange,
      @Value("${tacocloud.messaging.rabbit.routing-key}") String routingKey) {
    this.rabbit = rabbit;
    this.exchange = exchange;
    this.routingKey = routingKey;
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    rabbit.convertAndSend(exchange, routingKey, event,
        message -> {
          MessageProperties props = message.getMessageProperties();
          props.setMessageId(event.getEventId());
          props.setHeader("X_ORDER_SOURCE", "WEB");
          // El correlationId viaja también como header para que la DLQ lo conserve.
          props.setHeader(CORRELATION_HEADER, event.getCorrelationId());
          return message;
        });
  }

}
