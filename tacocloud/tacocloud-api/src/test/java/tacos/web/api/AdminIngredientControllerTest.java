package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.api.dto.IngredientMapper;
import tacos.api.error.BusinessRuleException;
import tacos.data.IngredientRepository;
import tacos.inventory.InventoryService;
import tacos.testsupport.Mvc;

// TC-13: catálogo con precio, disponibilidad y stock.
class AdminIngredientControllerTest {

    private final IngredientRepository repo = mock(IngredientRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final MockMvc mvc = Mvc.standalone(new AdminIngredientController(repo, new IngredientMapper(), inventory));

    private Ingredient ingredient(boolean available, int stock) {
        Ingredient i = new Ingredient("CHED", "Cheddar", Ingredient.Type.CHEESE);
        i.setUnitPrice(new BigDecimal("0.60"));
        i.setAvailable(available);
        i.setStockOnHand(stock);
        return i;
    }

    @Test
    void availableWithoutStockIsInconsistent() {
        assertThatThrownBy(() -> InventoryService.checkCatalogConsistency(ingredient(true, 0)))
            .isInstanceOf(BusinessRuleException.class);
        // Pausa comercial válida: hay stock pero no se vende.
        InventoryService.checkCatalogConsistency(ingredient(false, 20));
    }

    @Test
    void negativePriceIsRejectedBeforeTouchingTheCatalog() throws Exception {
        Mvc.perform(mvc, patch("/api/v1/admin/ingredients/CHED/catalog")
                .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":-0.01}"))
            .andExpect(status().isBadRequest());
        verify(repo, never()).save(any());
    }

    @Test
    void versionConflictIsTranslatedTo409() throws Exception {
        when(repo.findById("CHED")).thenReturn(Mono.just(ingredient(true, 5)));
        when(repo.save(any())).thenReturn(Mono.error(new OptimisticLockingFailureException("stale version")));

        Mvc.perform(mvc, patch("/api/v1/admin/ingredients/CHED/catalog")
                .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\":0.70}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void adjustmentThatWouldLeaveNegativeStockIs422() throws Exception {
        when(inventory.adjustStock("CHED", -50)).thenReturn(Mono.error(
            new BusinessRuleException("NEGATIVE_STOCK", "The adjustment would leave negative stock.")));

        Mvc.perform(mvc, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/admin/ingredients/CHED/stock-adjustments")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-50}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("NEGATIVE_STOCK"));
    }

    @Test
    void adminViewIncludesOperationalData() {
        Ingredient i = ingredient(true, 7);
        i.setVersion(2L);
        assertThat(new IngredientMapper().toAdminResponse(i).getStockOnHand()).isEqualTo(7);
    }
}
