package tacos.web.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.ratings")
public class RatingProperties {
    // TC-22: mínimo de votos para entrar al ranking (evita el "5.0 de una persona").
    private int minVotes = 3;
    private int maxLimit = 50;
}
