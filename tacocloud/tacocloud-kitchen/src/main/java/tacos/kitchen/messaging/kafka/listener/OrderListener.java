package tacos.kitchen.messaging.kafka.listener;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tacos.messaging.contract.OrderEvent;
import tacos.kitchen.KitchenUI;
import tacos.kitchen.ProcessedEvent;
import tacos.kitchen.ProcessedEventRepository;

import java.time.Instant;

@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="kafka")
@Component
public class OrderListener {
  
  private KitchenUI ui;
  private final ProcessedEventRepository processedEventRepository;

  public OrderListener(KitchenUI ui, ProcessedEventRepository processedEventRepository) {
    this.ui = ui;
    this.processedEventRepository = processedEventRepository;
  }

  @KafkaListener(topics = "tacocloud.orders.topic")
  public void handle(OrderEvent event) {
    if (processedEventRepository.existsById(event.getEventId())) {
        return;
    }
    
    // ui.displayOrder(order);
    processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now(), "SUCCESS"));
  }
}
