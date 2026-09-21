package tacos.kitchen.messaging.jms.listener;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tacos.messaging.contract.OrderEvent;
import tacos.kitchen.KitchenUI;
import tacos.kitchen.ProcessedEvent;
import tacos.kitchen.ProcessedEventRepository;

import java.time.Instant;

@ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="jms")
@Component
public class OrderListener {
  
  private KitchenUI ui;
  private final ProcessedEventRepository processedEventRepository;

  public OrderListener(KitchenUI ui, ProcessedEventRepository processedEventRepository) {
    this.ui = ui;
    this.processedEventRepository = processedEventRepository;
  }

  @JmsListener(destination = "tacocloud.order.queue")
  public void receiveOrder(OrderEvent event) {
    if (processedEventRepository.existsById(event.getEventId())) {
        return;
    }
    
    // ui.displayOrder(order);
    processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now(), "SUCCESS"));
  }
}
