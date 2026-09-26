package tacos.web.api;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.idempotency")
public class IdempotencyProperties {
    // Cuánto tiempo se conserva una llave antes de que Mongo la elimine.
    private Duration retention = Duration.ofHours(24);
    // Un IN_PROGRESS más viejo que esto se considera abandonado (el proceso murió).
    private Duration inProgressTimeout = Duration.ofSeconds(30);
}
