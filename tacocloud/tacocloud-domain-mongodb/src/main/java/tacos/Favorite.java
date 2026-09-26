package tacos;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-21: sólo IDs, nunca el objeto User completo.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "favorites")
@CompoundIndex(name = "user_taco_idx", def = "{'userId': 1, 'tacoId': 1}", unique = true)
public class Favorite {
    @Id
    private String id;
    private String userId;
    private String tacoId;
    private Instant createdAt;
}
