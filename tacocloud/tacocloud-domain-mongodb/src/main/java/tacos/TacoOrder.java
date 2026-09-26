package tacos;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
@CompoundIndexes({
  // TC-23: historial por usuario ordenado por fecha e ID.
  @CompoundIndex(name = "user_history_idx", def = "{'userId': 1, 'placedAt': -1, '_id': -1}"),
  // TC-26: cola de cocina FIFO por estado.
  @CompoundIndex(name = "status_queue_idx", def = "{'status': 1, 'placedAt': 1, '_id': 1}")
})
public class TacoOrder implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  private String id;
  private Date placedAt = new Date();

  // Sólo el identificador del dueño; nunca el objeto User embebido.
  private String userId;

  private String deliveryName;

  private String deliveryStreet;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryZip;

  // TC-12: referencia al método tokenizado + snapshot no sensible para mostrar.
  private String paymentMethodId;
  private String paymentBrand;
  private String paymentLast4;

  // TC-15: máximo un cupón por orden.
  private String discountCode;
  private BigDecimal discountAmount = BigDecimal.ZERO;

  // TC-14: moneda y totales calculados en el servidor.
  private String currency;
  private BigDecimal subtotal = BigDecimal.ZERO;
  private BigDecimal total = BigDecimal.ZERO;

  private List<OrderItem> items = new ArrayList<>();

  // TC-25: ciclo de vida con historial y control de concurrencia.
  @Version
  private Long version;

  private OrderStatus status = OrderStatus.CREATED;
  private List<OrderStatusHistory> statusHistory = new ArrayList<>();

  // TC-26: datos de la cocina.
  private String stationId;
  private String cookId;
  private Integer estimatedPrepMinutes;

  public void addItem(OrderItem item) {
    this.items.add(item);
  }

}
