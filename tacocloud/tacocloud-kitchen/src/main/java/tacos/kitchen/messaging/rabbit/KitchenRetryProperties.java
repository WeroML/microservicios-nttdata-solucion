package tacos.kitchen.messaging.rabbit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

// TC-30: reintentos limitados con backoff exponencial para errores transitorios.
@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.kitchen.retry")
public class KitchenRetryProperties {
    private int maxAttempts = 3;
    private Duration initialInterval = Duration.ofMillis(500);
    private double multiplier = 2.0;
    private Duration maxInterval = Duration.ofSeconds(5);
}
