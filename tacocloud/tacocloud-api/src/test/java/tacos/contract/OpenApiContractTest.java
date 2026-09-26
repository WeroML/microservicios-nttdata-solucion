package tacos.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import tacos.api.dto.OrderMapper;
import tacos.api.dto.IngredientMapper;
import tacos.api.dto.TacoMapper;
import tacos.api.error.NotFoundException;
import tacos.testsupport.Fixtures;
import tacos.testsupport.Mvc;

// TC-35: el contrato versionado se valida en el build y se contrasta con respuestas reales.
class OpenApiContractTest {

    private static OpenAPI spec;
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @BeforeAll
    static void parse() {
        SwaggerParseResult result = new OpenAPIV3Parser()
            .readLocation("src/main/resources/static/openapi.yaml", null, null);
        assertThat(result.getMessages()).as("errores de validación del spec").isEmpty();
        spec = result.getOpenAPI();
    }

    @Test
    void everyImplementedEndpointIsDocumented() {
        assertThat(spec.getPaths().keySet()).contains(
            "/ingredients", "/ingredients/{id}", "/admin/ingredients/{id}/catalog",
            "/admin/ingredients/{id}/stock-adjustments", "/tacos", "/tacos/validate", "/tacos/today",
            "/tacos/top", "/tacos/{id}", "/tacos/{id}/classification", "/tacos/{id}/rating",
            "/orders", "/orders/quote", "/orders/fromEmail", "/orders/{id}", "/orders/{id}/cancel",
            "/orders/{id}/status", "/orders/{id}/reorder", "/users/me/orders", "/users/me/orders/{id}",
            "/users/me/favorites", "/users/me/favorites/{tacoId}", "/admin/orders", "/kitchen/queue",
            "/kitchen/orders/claim", "/coupons/validate", "/payment-methods", "/payment-methods/tokenize",
            "/announcements", "/admin/announcements", "/admin/announcements/{id}");
        assertThat(spec.getServers()).extracting("url").contains("/api/v1", "/api");
    }

    @Test
    void schemasHaveNoSensitiveFieldsInResponses() {
        List<String> forbidden = Arrays.asList("password", "ccNumber", "ccCVV", "authorities",
            "paymentToken", "userId", "user", "token");
        for (Map.Entry<String, Schema> entry : spec.getComponents().getSchemas().entrySet()) {
            if (entry.getKey().endsWith("Request")) {
                continue;
            }
            assertThat(allProperties(entry.getValue())).as(entry.getKey()).doesNotContainAnyElementsOf(forbidden);
        }
    }

    // Contract test: una OrderResponse real sólo trae propiedades del schema y todas las requeridas.
    @Test
    void orderResponseMatchesTheSchema() throws Exception {
        tacos.TacoOrder order = Fixtures.draftFactory(Fixtures.catalog())
            .build(Fixtures.USER_ID, Fixtures.simpleOrderRequest()).block();
        order.setId("order-1");
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
            new OrderMapper(new TacoMapper(new IngredientMapper())).toResponse(order)));

        assertMatches(json, spec.getComponents().getSchemas().get("OrderResponse"));
    }

    @RestController
    @RequestMapping(path = {"/api/v1/probe", "/api/probe"})
    static class ProbeController {
        @GetMapping
        reactor.core.publisher.Mono<String> probe() {
            return reactor.core.publisher.Mono.error(new NotFoundException("ORDER_NOT_FOUND", "Order not found."));
        }
    }

    @Test
    void apiProblemMatchesTheSchema() throws Exception {
        MockMvc mvc = Mvc.standalone(new ProbeController());
        String body = Mvc.perform(mvc, get("/api/v1/probe")).andReturn().getResponse().getContentAsString();

        assertMatches(mapper.readTree(body), spec.getComponents().getSchemas().get("ApiProblem"));
    }

    // Detecta un campo incompatible: un JSON con un campo extra o sin uno requerido falla.
    @Test
    void incompatibleFieldIsDetected() throws Exception {
        Schema schema = spec.getComponents().getSchemas().get("ApiProblem");
        JsonNode extra = mapper.readTree("{\"type\":\"t\",\"title\":\"t\",\"status\":404,\"instance\":\"/x\","
            + "\"code\":\"C\",\"stackTrace\":\"boom\"}");
        JsonNode missing = mapper.readTree("{\"type\":\"t\",\"title\":\"t\",\"status\":404}");

        assertThat(violations(extra, schema)).contains("unexpected property stackTrace");
        assertThat(violations(missing, schema)).contains("missing required instance", "missing required code");
    }

    @Test
    void legacyAliasIsServedAndMarkedAsDeprecated() throws Exception {
        MockMvc mvc = Mvc.standalone(new ProbeController());

        Mvc.perform(mvc, get("/api/probe"))
            .andExpect(status().isNotFound())
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().string("Link", "</api/v1/probe>; rel=\"successor-version\""));
        Mvc.perform(mvc, get("/api/v1/probe"))
            .andExpect(header().doesNotExist("Deprecation"));
        Mvc.perform(mvc, post("/api/v1/probe").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed());
    }

    private static void assertMatches(JsonNode json, Schema schema) {
        assertThat(violations(json, schema)).isEmpty();
    }

    private static List<String> violations(JsonNode json, Schema schema) {
        List<String> problems = new ArrayList<>();
        Set<String> allowed = allProperties(schema);
        Iterator<String> fields = json.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                problems.add("unexpected property " + field);
            }
        }
        if (schema.getRequired() != null) {
            for (Object required : schema.getRequired()) {
                if (!json.has((String) required)) {
                    problems.add("missing required " + required);
                }
            }
        }
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> allProperties(Schema schema) {
        Set<String> names = new TreeSet<>();
        if (schema.getProperties() != null) {
            names.addAll(schema.getProperties().keySet());
        }
        if (schema instanceof ComposedSchema && ((ComposedSchema) schema).getAllOf() != null) {
            for (Schema part : ((ComposedSchema) schema).getAllOf()) {
                if (part.get$ref() != null) {
                    String name = part.get$ref().substring(part.get$ref().lastIndexOf('/') + 1);
                    names.addAll(allProperties(spec.getComponents().getSchemas().get(name)));
                } else {
                    names.addAll(allProperties(part));
                }
            }
        }
        return names;
    }
}
