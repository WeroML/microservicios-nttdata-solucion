package tacos.web.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

// TC-19: límites seguros de búsqueda.
@Data
@Component
@ConfigurationProperties(prefix = "tacocloud.search")
public class TacoSearchProperties {
    private int maxPageSize = 50;
    private int maxNameLength = 50;
    private List<String> sortableFields = Arrays.asList("createdAt", "name");
}
