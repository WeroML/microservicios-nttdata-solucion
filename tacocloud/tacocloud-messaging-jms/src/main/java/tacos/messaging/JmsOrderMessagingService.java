package tacos.messaging;

import javax.jms.JMSException;
import javax.jms.Message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

@Service
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="jms")
public class JmsOrderMessagingService implements OrderMessagingService {

  private final JmsTemplate jms;
  private final String destination;

  public JmsOrderMessagingService(JmsTemplate jms,
      @Value("${tacocloud.messaging.jms.destination}") String destination) {
    this.jms = jms;
    this.destination = destination;
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    jms.convertAndSend(destination, event, message -> addHeaders(message, event));
  }

  private Message addHeaders(Message message, OrderEvent event) throws JMSException {
    message.setStringProperty("X_ORDER_SOURCE", "WEB");
    message.setJMSCorrelationID(event.getCorrelationId());
    return message;
  }

}
