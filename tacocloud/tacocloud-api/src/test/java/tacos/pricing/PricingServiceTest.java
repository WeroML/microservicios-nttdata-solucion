package tacos.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tacos.Ingredient;
import tacos.OrderItem;
import tacos.Taco;
import tacos.api.error.BusinessRuleException;
import tacos.testsupport.Fixtures;

// TC-14: precios del lado servidor con BigDecimal, HALF_UP y snapshot histórico.
class PricingServiceTest {

    private final PricingService pricing = Fixtures.pricing();
    private final Map<String, Ingredient> catalog = Fixtures.catalog();

    @Test
    void decimalsAreSummedAndRoundedHalfUp() {
        Ingredient a = Fixtures.ingredient("A", Ingredient.Type.SAUCE, "0.105", java.util.Collections.emptySet(),
            java.util.Collections.emptySet(), Ingredient.SpiceLevel.NONE);
        Ingredient b = Fixtures.ingredient("B", Ingredient.Type.SAUCE, "0.10", java.util.Collections.emptySet(),
            java.util.Collections.emptySet(), Ingredient.SpiceLevel.NONE);

        assertThat(pricing.unitPrice(Arrays.asList(a, b))).isEqualTo(new BigDecimal("0.21"));
    }

    @Test
    void twoUnitsDoubleTheLineSubtotal() {
        Taco taco = Fixtures.taco(null, "Classic", catalog, "FLTO", "GRBF", "CHED");

        OrderItem one = pricing.priceLine(taco, 1);
        OrderItem two = pricing.priceLine(taco, 2);

        assertThat(two.getSubtotal()).isEqualByComparingTo(one.getSubtotal().multiply(BigDecimal.valueOf(2)));
    }

    @Test
    void zeroNegativeAndExcessiveQuantitiesFail() {
        Taco taco = Fixtures.taco(null, "Classic", catalog, "FLTO", "GRBF");
        for (int quantity : new int[] {0, -1, 11}) {
            assertThatThrownBy(() -> pricing.priceLine(taco, quantity)).isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    void historicalPriceStaysStableWhenCatalogChanges() {
        Taco taco = Fixtures.taco(null, "Classic", catalog, "FLTO", "GRBF");
        OrderItem line = pricing.priceLine(taco, 1);
        BigDecimal atPurchase = line.getUnitPriceAtPurchase();

        catalog.get("GRBF").setUnitPrice(new BigDecimal("9.99"));

        assertThat(line.getUnitPriceAtPurchase()).isEqualTo(atPurchase);
        assertThat(line.getSubtotal()).isEqualByComparingTo("1.75");
    }
}
