package tacos;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderItem {
    private Taco taco;
    private int quantity;
    private BigDecimal unitPriceAtPurchase;
    private BigDecimal subtotal;
}
