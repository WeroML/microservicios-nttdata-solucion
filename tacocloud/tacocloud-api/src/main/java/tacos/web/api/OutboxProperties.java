package tacos.web.api;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

// TC-29: parámetros del publicador del outbox.
@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.outbox")
public class OutboxProperties {
    private int batchSize = 20;
    private int maxAttempts = 5;
    private Duration initialBackoff = Duration.ofSeconds(2);
    private Duration maxBackoff = Duration.ofMinutes(5);
    // Un claim PUBLISHING más viejo que esto se considera abandonado (reinicio) y se retoma.
    private Duration claimTimeout = Duration.ofMinutes(1);
}
