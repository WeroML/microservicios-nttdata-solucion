package tacos.validation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;
import tacos.Ingredient.SpiceLevel;

// TC-18: configuración de las reglas "divertidas"; sin IDs mágicos en el código.
@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.taco-rules")
public class TacoRulesProperties {

    private SpicyNeedsCheese spicyNeedsCheese = new SpicyNeedsCheese();
    private VeganName veganName = new VeganName();

    @Data
    public static class SpicyNeedsCheese {
        private boolean enabled = true;
        private SpiceLevel minSpiceLevel = SpiceLevel.HOT;
    }

    @Data
    public static class VeganName {
        private boolean enabled = true;
        private String keyword = "vegan";
    }
}
