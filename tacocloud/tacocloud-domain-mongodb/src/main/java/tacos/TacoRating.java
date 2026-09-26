package tacos;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-22: un voto por usuario y taco, garantizado por índice único.
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
    private int score;
    private Instant createdAt;
    private Instant updatedAt;
}
