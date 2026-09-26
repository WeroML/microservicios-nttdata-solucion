package tacos.web.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.kitchen")
public class KitchenProperties {

    // Estación de este proceso; el cocinero sale de la identidad autenticada.
    private String stationId = "STATION-1";
    private Eta eta = new Eta();

    /**
     * TC-26: ETA = base + perTaco x unidades + perIngredient x ingredientes distintos
     *            + perOrderAhead x órdenes por delante.
     * Es una estimación determinista, no una promesa.
     */
    @Data
    public static class Eta {
        private int baseMinutes = 5;
        private int perTacoMinutes = 2;
        private int perIngredientMinutes = 1;
        private int perOrderAheadMinutes = 3;
    }
}
