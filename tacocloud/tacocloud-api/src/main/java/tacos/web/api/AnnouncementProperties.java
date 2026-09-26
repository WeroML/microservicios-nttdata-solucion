package tacos.web.api;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.announcements")
public class AnnouncementProperties {
    private int maxActive = 10;
    private Duration defaultTtl = Duration.ofDays(7);
    private Duration maxTtl = Duration.ofDays(30);
}
