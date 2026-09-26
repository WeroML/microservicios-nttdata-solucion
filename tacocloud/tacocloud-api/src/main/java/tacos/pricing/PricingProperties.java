package tacos.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.pricing")
public class PricingProperties {
    // TC-14: moneda definida y máximo de unidades por línea configurable.
    private String currency = "USD";
    private int maxQuantityPerLine = 20;
}
