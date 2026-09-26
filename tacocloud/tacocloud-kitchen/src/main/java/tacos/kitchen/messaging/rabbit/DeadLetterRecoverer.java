package tacos.kitchen.messaging.rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * TC-30: mensaje agotado o con error permanente -> DLQ.
 * Conserva los headers originales (incluido X-Correlation-Id) y agrega la causa
 * (tipo y mensaje de la excepción raíz, que no contienen datos sensibles). Sin stack trace.
 */
public class DeadLetterRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoverer.class);

    public static final String CAUSE_TYPE_HEADER = "x-exception-type";
    public static final String CAUSE_MESSAGE_HEADER = "x-exception-message";
    public static final String ORIGINAL_QUEUE_HEADER = "x-original-queue";

    private final RabbitTemplate rabbitTemplate;
    private final String deadLetterQueue;
    private final MeterRegistry meterRegistry;

    public DeadLetterRecoverer(RabbitTemplate rabbitTemplate, String deadLetterQueue, MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.deadLetterQueue = deadLetterQueue;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        Throwable root = rootCause(cause);
        MessageProperties props = message.getMessageProperties();
        props.setHeader(CAUSE_TYPE_HEADER, root.getClass().getName());
        props.setHeader(CAUSE_MESSAGE_HEADER, root.getMessage());
        props.setHeader(ORIGINAL_QUEUE_HEADER, props.getConsumerQueue());
        rabbitTemplate.send("", deadLetterQueue, message);
        meterRegistry.counter("tacocloud.kitchen.events", "result", "dlq").increment();
        log.warn("Message {} sent to {} after failure: {}", props.getMessageId(), deadLetterQueue,
            root.getClass().getSimpleName());
    }

    static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
