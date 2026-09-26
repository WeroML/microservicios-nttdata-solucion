package tacos.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import tacos.Ingredient;
import tacos.OrderItem;
import tacos.Taco;
import tacos.api.error.BusinessRuleException;

/**
 * TC-14: precios calculados en el servidor con precios del catálogo.
 *
 * Política:
 * - Precio unitario del taco = suma de unitPrice de sus ingredientes.
 * - Subtotal de línea = precio unitario x cantidad.
 * - Escala 2 decimales, RoundingMode.HALF_UP, moneda de tacocloud.pricing.currency.
 * - El precio usado se guarda en la línea (unitPriceAtPurchase) como snapshot:
 *   cambios posteriores del catálogo no alteran órdenes históricas.
 */
@Service
public class PricingService {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final PricingProperties properties;

    public PricingService(PricingProperties properties) {
        this.properties = properties;
    }

    public String currency() {
        return properties.getCurrency();
    }

    public BigDecimal unitPrice(List<Ingredient> ingredients) {
        BigDecimal total = BigDecimal.ZERO;
        for (Ingredient i : ingredients) {
            if (i.getUnitPrice() == null) {
                throw new BusinessRuleException("PRICE_NOT_DEFINED",
                    "Ingredient " + i.getId() + " has no price.");
            }
            total = total.add(i.getUnitPrice());
        }
        return total.setScale(SCALE, ROUNDING);
    }

    public OrderItem priceLine(Taco taco, int quantity) {
        if (quantity < 1 || quantity > properties.getMaxQuantityPerLine()) {
            throw new BusinessRuleException("INVALID_QUANTITY",
                "Quantity must be between 1 and " + properties.getMaxQuantityPerLine() + ".");
        }
        BigDecimal unitPrice = unitPrice(taco.getIngredients());
        OrderItem item = new OrderItem();
        item.setTaco(taco);
        item.setQuantity(quantity);
        item.setUnitPriceAtPurchase(unitPrice);
        item.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING));
        return item;
    }

    public BigDecimal subtotal(List<OrderItem> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : items) {
            subtotal = subtotal.add(item.getSubtotal());
        }
        return subtotal.setScale(SCALE, ROUNDING);
    }
}
