package tacos.data;

import reactor.core.publisher.Flux;
import tacos.Taco;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface TacoSearchRepository {
    Flux<Taco> searchTacos(String name, String ingredientId, List<String> dietaryTags, List<String> excludeAllergens, String spiceLevel, Pageable pageable);
}
