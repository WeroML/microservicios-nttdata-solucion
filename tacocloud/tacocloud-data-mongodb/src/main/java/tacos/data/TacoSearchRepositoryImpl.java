package tacos.data;

import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import reactor.core.publisher.Flux;
import tacos.Taco;

public class TacoSearchRepositoryImpl implements TacoSearchRepository {

    private final ReactiveMongoTemplate mongoTemplate;

    public TacoSearchRepositoryImpl(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // TC-19: la consulta se arma y ejecuta en Mongo; nunca se filtra en memoria.
    @Override
    public Flux<Taco> searchTacos(TacoSearchCriteria criteria, Sort sort, long offset, int limit) {
        Query query = new Query().with(sort).skip(offset).limit(limit);

        if (criteria.getName() != null && !criteria.getName().isEmpty()) {
            // Pattern.quote escapa el texto: el usuario no puede inyectar una regex costosa.
            query.addCriteria(Criteria.where("name").regex(Pattern.quote(criteria.getName()), "i"));
        }

        if (criteria.getIngredientId() != null && !criteria.getIngredientId().isEmpty()) {
            query.addCriteria(Criteria.where("ingredients._id").is(criteria.getIngredientId()));
        }

        if (criteria.getDiet() != null) {
            query.addCriteria(Criteria.where("dietaryTags").is(criteria.getDiet()));
        }

        if (criteria.getExcludeAllergens() != null && !criteria.getExcludeAllergens().isEmpty()) {
            query.addCriteria(Criteria.where("allergens").nin(criteria.getExcludeAllergens()));
        }

        if (criteria.getSpice() != null) {
            query.addCriteria(Criteria.where("spiceLevel").is(criteria.getSpice()));
        }

        return mongoTemplate.find(query, Taco.class);
    }
}
