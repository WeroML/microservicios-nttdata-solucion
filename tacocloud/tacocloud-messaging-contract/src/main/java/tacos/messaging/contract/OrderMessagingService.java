package tacos.messaging.contract;

/**
 * TC-27/TC-28: puerto único que implementan los adaptadores noop, JMS, Rabbit y Kafka.
 *
 * El método es síncrono: termina cuando el broker aceptó el evento o lanza
 * una excepción. El outbox (TC-29) lo encapsula dentro de su cadena reactiva.
 */
public interface OrderMessagingService {
    void sendOrderEvent(OrderEvent event);
}
