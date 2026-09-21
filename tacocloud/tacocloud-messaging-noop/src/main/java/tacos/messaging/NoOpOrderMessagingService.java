package tacos.messaging;

import org.springframework.stereotype.Service;
import tacos.messaging.contract.OrderEvent;
import tacos.messaging.contract.OrderMessagingService;
import lombok.extern.slf4j.Slf4j;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="tacocloud.messaging.transport", havingValue="noop", matchIfMissing=true)
@Slf4j
public class NoOpOrderMessagingService implements OrderMessagingService {

    @Override
    public void sendOrderEvent(OrderEvent event) {
        log.info("No-op messaging: received event {}", event);
    }

}
