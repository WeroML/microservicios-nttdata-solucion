package tacos.data;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import tacos.Taco;
import java.util.List;

public class TacoSearchRepositoryImpl implements TacoSearchRepository {

    private final ReactiveMongoTemplate mongoTemplate;

    public TacoSearchRepositoryImpl(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Flux<Taco> searchTacos(String name, String ingredientId, List<String> dietaryTags, List<String> excludeAllergens, String spiceLevel, Pageable pageable) {
        Query query = new Query().with(pageable);

        if (name != null && !name.isEmpty()) {
            query.addCriteria(Criteria.where("name").regex(name, "i"));
        }

        if (ingredientId != null && !ingredientId.isEmpty()) {
            query.addCriteria(Criteria.where("ingredients").elemMatch(Criteria.where("_id").is(ingredientId)));
        }

        if (dietaryTags != null && !dietaryTags.isEmpty()) {
            query.addCriteria(Criteria.where("dietaryTags").all(dietaryTags));
        }

        if (excludeAllergens != null && !excludeAllergens.isEmpty()) {
            query.addCriteria(Criteria.where("allergens").nin(excludeAllergens));
        }

        if (spiceLevel != null && !spiceLevel.isEmpty()) {
            query.addCriteria(Criteria.where("spiceLevel").is(spiceLevel));
        }

        return mongoTemplate.find(query, Taco.class);
    }
}
