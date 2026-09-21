package tacos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
public class TacoOrder implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  private String id;
  private Date placedAt = new Date();

  private User user;

  private String deliveryName;

  private String deliveryStreet;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryZip;

  private String paymentMethodId;
  private String discountCode;
  private java.math.BigDecimal discountAmount = java.math.BigDecimal.ZERO;
  
  @org.springframework.data.annotation.Version
  private Long version;
  
  private OrderStatus status = OrderStatus.CREATED;
  private List<OrderStatusHistory> statusHistory = new ArrayList<>();
  
  private String stationId;
  private String cookId;
  private Integer estimatedPrepMinutes;
  
  private List<OrderItem> items = new ArrayList<>();
  private java.math.BigDecimal total = java.math.BigDecimal.ZERO;

  public void addItem(OrderItem item) {
    this.items.add(item);
  }

}
