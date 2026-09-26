package tacos.discount;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// TC-15/TC-20: reloj inyectable con zona configurada; las pruebas usan Clock.fixed.
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(@Value("${tacocloud.clock.zone:America/Mexico_City}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
