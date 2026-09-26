package tacos;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

// TC-12: sólo se guarda el token del gateway y datos no sensibles.
// Nunca PAN ni CVV.
@Document
@Data
@NoArgsConstructor(force=true, access=AccessLevel.PRIVATE)
@RequiredArgsConstructor(onConstructor_ = @PersistenceConstructor)
public class PaymentMethod {

  @Id
  private String id;

  @Indexed
  private final String userId;
  private final String paymentToken;
  private final String brand;
  private final String last4;
  private final String expiration;

}
