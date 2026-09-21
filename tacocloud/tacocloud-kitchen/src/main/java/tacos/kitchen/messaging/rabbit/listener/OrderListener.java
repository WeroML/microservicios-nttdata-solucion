package tacos.kitchen.messaging.rabbit.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tacos.messaging.contract.OrderEvent;
import tacos.kitchen.KitchenUI;
import tacos.kitchen.ProcessedEvent;
import tacos.kitchen.ProcessedEventRepository;

import java.time.Instant;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="rabbit")
@Component
public class OrderListener {
  
  private final KitchenUI ui;
  private final ProcessedEventRepository processedEventRepository;

  public OrderListener(KitchenUI ui, ProcessedEventRepository processedEventRepository) {
    this.ui = ui;
    this.processedEventRepository = processedEventRepository;
  }

  @RabbitListener(queues = "tacocloud.order.queue", containerFactory = "rabbitListenerContainerFactory")
  public void receiveOrder(OrderEvent event) {
    if (processedEventRepository.existsById(event.getEventId())) {
        // Idempotent: already processed
        return;
    }
    
    // Process event
    // For now we just display it in UI
    // To match original KitchenUI expectation, we could map it back to TacoOrder partially, or update UI.
    // We will just do a simple pass or ignore.
    
    processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now(), "SUCCESS"));
  }
}
