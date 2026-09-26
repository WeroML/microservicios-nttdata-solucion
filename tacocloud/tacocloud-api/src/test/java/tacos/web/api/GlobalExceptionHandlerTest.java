package tacos.web.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.validation.Valid;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.OrderCreateRequest;
import tacos.api.error.BusinessRuleException;
import tacos.api.error.ConflictException;
import tacos.api.error.NotFoundException;
import tacos.testsupport.Mvc;

// TC-09: todos los errores controlados comparten la estructura ApiProblem.
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @PostMapping("/api/v1/probe/orders")
        Mono<String> create(@Valid @RequestBody OrderCreateRequest request) {
            return Mono.just("ok");
        }

        @GetMapping("/api/v1/probe/missing")
        Mono<String> missing() {
            return Mono.error(new NotFoundException("ORDER_NOT_FOUND", "Order not found."));
        }

        @GetMapping("/api/v1/probe/conflict")
        Mono<String> conflict() {
            return Mono.error(new ConflictException("INVALID_TRANSITION", "Cannot change order."));
        }

        @GetMapping("/api/v1/probe/rule")
        Mono<String> rule() {
            return Mono.error(new BusinessRuleException("INVALID_QUANTITY", "Quantity must be between 1 and 20."));
        }

        @GetMapping("/api/v1/probe/driver")
        Mono<String> driver() {
            return Mono.error(new DataAccessResourceFailureException(
                "Timed out after 30000 ms while waiting for a server that matches com.mongodb.client.internal"));
        }
    }

    private final MockMvc mvc = Mvc.standalone(new ProbeController());

    @Test
    void invalidOrderListsEveryFieldAndReason() throws Exception {
        String body = "{\"deliveryName\":\"\",\"deliveryState\":\"Texas\",\"deliveryZip\":\"abc\","
            + "\"items\":[{\"quantity\":0,\"taco\":{\"name\":\"abc\",\"ingredientIds\":[]}}]}";

        Mvc.perform(mvc, post("/api/v1/probe/orders").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.instance").value("/api/v1/probe/orders"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'deliveryZip')]").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'deliveryState')]").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'paymentMethodId')]").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'items[0].quantity')]").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'items[0].taco.name')]").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'items[0].taco.ingredientIds')]").exists());
    }

    @Test
    void notFoundConflictAndBusinessRuleKeepTheirStatus() throws Exception {
        Mvc.perform(mvc, get("/api/v1/probe/missing"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        Mvc.perform(mvc, get("/api/v1/probe/conflict"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        Mvc.perform(mvc, get("/api/v1/probe/rule"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"))
            .andExpect(jsonPath("$.title").value("Unprocessable Entity"));
    }

    @Test
    void driverErrorsNeverLeakStacktraceOrDriverNames() throws Exception {
        Mvc.perform(mvc, get("/api/v1/probe/driver"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(not(containsString("mongodb"))))
            .andExpect(content().string(not(containsString("Exception"))))
            .andExpect(content().string(not(containsString("at tacos."))));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        Mvc.perform(mvc, post("/api/v1/probe/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
