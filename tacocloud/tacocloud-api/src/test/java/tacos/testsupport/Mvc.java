package tacos.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tacos.web.api.ApiVersioningFilter;
import tacos.web.api.CorrelationIdFilter;
import tacos.web.api.GlobalExceptionHandler;

/**
 * MockMvc con el mismo stack MVC del runtime (filtros de correlación y versión,
 * @RestControllerAdvice y Bean Validation). Resuelve el dispatch asíncrono que usa
 * MVC cuando el controlador devuelve Mono/Flux.
 */
public final class Mvc {

    private Mvc() {
    }

    public static MockMvc standalone(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter(), new ApiVersioningFilter())
            .build();
    }

    public static ResultActions perform(MockMvc mvc, RequestBuilder request) throws Exception {
        MvcResult first = mvc.perform(request).andReturn();
        if (first.getRequest().isAsyncStarted()) {
            first.getAsyncResult(5000);
            return mvc.perform(asyncDispatch(first));
        }
        return wrap(first);
    }

    private static ResultActions wrap(MvcResult result) {
        return new ResultActions() {
            @Override
            public ResultActions andExpect(ResultMatcher matcher) throws Exception {
                matcher.match(result);
                return this;
            }

            @Override
            public ResultActions andDo(ResultHandler handler) throws Exception {
                handler.handle(result);
                return this;
            }

            @Override
            public MvcResult andReturn() {
                return result;
            }
        };
    }
}
