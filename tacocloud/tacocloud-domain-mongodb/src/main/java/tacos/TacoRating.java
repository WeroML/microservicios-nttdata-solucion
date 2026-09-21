package tacos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "taco_ratings")
@CompoundIndex(name = "user_taco_rating_idx", def = "{'userId': 1, 'tacoId': 1}", unique = true)
public class TacoRating {
    @Id
    private String id;
    private String userId;
    private String tacoId;
    private int score; // 1 to 5
    private Instant createdAt = Instant.now();
}
