package tacos.messaging.contract;

public interface OrderMessagingService {
    void sendOrderEvent(OrderEvent event);
}
