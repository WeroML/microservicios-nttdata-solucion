package tacos.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;

@Service
@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="kafka")
public class KafkaOrderMessagingService implements OrderMessagingService {

  private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
  private final String topic;
  private final long sendTimeoutSeconds;

  public KafkaOrderMessagingService(KafkaTemplate<String, OrderEvent> kafkaTemplate,
      @Value("${tacocloud.messaging.kafka.topic}") String topic,
      @Value("${tacocloud.messaging.kafka.send-timeout-seconds:10}") long sendTimeoutSeconds) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
    this.sendTimeoutSeconds = sendTimeoutSeconds;
  }

  // Espera la confirmación del broker: si falla, el outbox reintenta (TC-29).
  @Override
  public void sendOrderEvent(OrderEvent event) {
    try {
      kafkaTemplate.send(topic, event.getPayload().getOrderId(), event).get(sendTimeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while sending event " + event.getEventId(), e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Kafka did not confirm event " + event.getEventId(), e);
    }
  }

}
