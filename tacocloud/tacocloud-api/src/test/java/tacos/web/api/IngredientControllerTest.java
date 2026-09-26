package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.api.dto.AdminIngredientResponse;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.IngredientRequest;
import tacos.api.error.BadRequestException;
import tacos.api.error.NotFoundException;
import tacos.data.IngredientRepository;
import tacos.testsupport.Mvc;

// TC-01, TC-02, TC-03: CRUD reactivo de ingredientes con contrato HTTP verificable.
class IngredientControllerTest {

  private IngredientRepository repo;
  private IngredientController controller;
  private MockMvc mvc;
  private Ingredient flour;

  @BeforeEach
  void setUp() {
    repo = mock(IngredientRepository.class);
    controller = new IngredientController(repo, new IngredientMapper());
    mvc = Mvc.standalone(controller);
    flour = new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP);
    flour.setUnitPrice(new BigDecimal("0.50"));
    flour.setAvailable(true);
    flour.setStockOnHand(10);
    flour.setVersion(3L);
    when(repo.findById(any(String.class))).thenReturn(Mono.empty());
    when(repo.findById("FLTO")).thenReturn(Mono.just(flour));
    when(repo.save(any())).thenAnswer(inv -> Mono.justOrEmpty((Ingredient) inv.getArgument(0)));
  }

  private static IngredientRequest request(String id, String name) {
    IngredientRequest req = new IngredientRequest();
    req.setId(id);
    req.setName(name);
    req.setType(Ingredient.Type.WRAP);
    req.setUnitPrice(new BigDecimal("0.75"));
    req.setAvailable(true);
    req.setStockOnHand(5);
    return req;
  }

  // ---- TC-01 ----

  @Test
  void putUpdatesExistingIngredientAndReturns200() {
    StepVerifier.create(controller.updateIngredient("FLTO", request("FLTO", "Wheat Tortilla")))
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(response.getBody().getName()).isEqualTo("Wheat Tortilla");
          assertThat(response.getBody().getUnitPrice()).isEqualByComparingTo("0.75");
          // stockOnHand sólo cambia con stock-adjustments (TC-13)
          assertThat(response.getBody().getStockOnHand()).isEqualTo(10);
        })
        .verifyComplete();
  }

  @Test
  void putWithUnknownIdIs404AndDoesNotCreateADocument() {
    StepVerifier.create(controller.updateIngredient("NOPE", request("NOPE", "Nope")))
        .expectError(NotFoundException.class)
        .verify();
    verify(repo, never()).save(any());
  }

  @Test
  void putWithDifferentBodyIdIs400() {
    StepVerifier.create(controller.updateIngredient("FLTO", request("COTO", "Corn")))
        .expectError(BadRequestException.class)
        .verify();
    verify(repo, never()).save(any());
  }

  // Regresión del publisher fantasma: save() se ejecuta sólo al suscribirse, y una vez.
  @Test
  void saveRunsOnlyWhenTheReturnedChainIsSubscribed() {
    AtomicInteger writes = new AtomicInteger();
    when(repo.save(any())).thenAnswer(inv -> Mono.defer(() -> {
      writes.incrementAndGet();
      return Mono.just((Ingredient) inv.getArgument(0));
    }));

    Mono<ResponseEntity<AdminIngredientResponse>> result =
        controller.updateIngredient("FLTO", request("FLTO", "Wheat Tortilla"));
    assertThat(writes.get()).isZero();

    StepVerifier.create(result).expectNextCount(1).verifyComplete();
    assertThat(writes.get()).isEqualTo(1);
  }

  @Test
  void putContractOverHttp() throws Exception {
    String body = "{\"id\":\"FLTO\",\"name\":\"Wheat Tortilla\",\"type\":\"WRAP\",\"unitPrice\":0.75,\"available\":true}";
    Mvc.perform(mvc, put("/api/v1/ingredients/FLTO").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.name").value("Wheat Tortilla"));

    String mismatch = body.replace("\"id\":\"FLTO\"", "\"id\":\"COTO\"");
    Mvc.perform(mvc, put("/api/v1/ingredients/FLTO").contentType(MediaType.APPLICATION_JSON).content(mismatch))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ID_MISMATCH"));

    Mvc.perform(mvc, put("/api/v1/ingredients/NOPE").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("FLTO", "NOPE")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
  }

  // ---- TC-02 ----

  @Test
  void deleteExistingIs204WithoutBodyAndDeletesOnce() throws Exception {
    when(repo.delete(flour)).thenReturn(Mono.empty());

    Mvc.perform(mvc, delete("/api/v1/ingredients/FLTO"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(repo, times(1)).delete(flour);
  }

  @Test
  void deleteMissingIs404WithoutCallingDelete() throws Exception {
    Mvc.perform(mvc, delete("/api/v1/ingredients/NOPE"))
        .andExpect(status().isNotFound());
    verify(repo, never()).delete(any(Ingredient.class));
    verify(repo, never()).deleteById(any(String.class));
  }

  // ---- TC-03 ----

  @Test
  void postBuildsLocationFromCurrentRequestNotLocalhost() {
    when(repo.save(any())).thenAnswer(inv -> {
      Ingredient i = inv.getArgument(0);
      i.setId("generated-id");
      return Mono.just(i);
    });
    IngredientRequest req = request(null, "Spinach Wrap");
    UriComponentsBuilder currentRequest = UriComponentsBuilder.fromUriString("https://tacos.example.com:8443/shop");

    StepVerifier.create(controller.postIngredient(req, currentRequest))
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
          assertThat(response.getHeaders().getLocation().toString())
              .isEqualTo("https://tacos.example.com:8443/shop/api/v1/ingredients/generated-id");
          assertThat(response.getBody().getId()).isEqualTo("generated-id");
        })
        .verifyComplete();
  }

  @Test
  void postRespectsHostPortAndContextPathOverHttp() throws Exception {
    when(repo.save(any())).thenAnswer(inv -> {
      Ingredient i = inv.getArgument(0);
      i.setId("generated-id");
      return Mono.just(i);
    });
    String body = "{\"name\":\"Spinach Wrap\",\"type\":\"WRAP\",\"unitPrice\":0.60,\"available\":true,\"stockOnHand\":3}";

    Mvc.perform(mvc, post("http://proxy.local:9999/ctx/api/v1/ingredients").contextPath("/ctx")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://proxy.local:9999/ctx/api/v1/ingredients/generated-id"));
  }

  @Test
  void invalidBodyIs400AndIsNotSaved() throws Exception {
    String body = "{\"name\":\"\",\"type\":null,\"unitPrice\":-1}";

    Mvc.perform(mvc, post("/api/v1/ingredients").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.violations.length()").value(3));
    verify(repo, never()).save(any());
  }

  @Test
  void postRejectsClientAssignedId() throws Exception {
    String body = "{\"id\":\"MINE\",\"name\":\"Spinach Wrap\",\"type\":\"WRAP\",\"unitPrice\":0.60}";

    Mvc.perform(mvc, post("/api/v1/ingredients").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ID_NOT_ALLOWED"));
    verify(repo, never()).save(any());
  }

  @Test
  void catalogIsPublicViewWithoutOperationalData() throws Exception {
    when(repo.findAll()).thenReturn(reactor.core.publisher.Flux.just(flour));

    Mvc.perform(mvc, get("/api/v1/ingredients"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].unitPrice").value(0.50))
        .andExpect(jsonPath("$[0].available").value(true))
        .andExpect(jsonPath("$[0].stockOnHand").doesNotExist())
        .andExpect(jsonPath("$[0].version").doesNotExist());
  }
}
