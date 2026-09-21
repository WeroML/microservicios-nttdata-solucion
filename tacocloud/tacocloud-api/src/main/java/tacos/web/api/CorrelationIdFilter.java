package tacos.web.api;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty() || correlationId.length() > 100) {
            correlationId = UUID.randomUUID().toString();
        } else {
            // Sanitize
            correlationId = correlationId.replaceAll("[\\r\\n]", "").trim();
        }

        final String finalCorrelationId = correlationId;
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        return chain.filter(exchange)
                .contextWrite(Context.of(CORRELATION_ID_KEY, finalCorrelationId))
                .doOnEach(signal -> {
                    if (signal.isOnNext() || signal.isOnError() || signal.isOnComplete()) {
                        String cid = signal.getContextView().getOrDefault(CORRELATION_ID_KEY, "UNKNOWN");
                        MDC.put(CORRELATION_ID_KEY, cid);
                    }
                })
                .doFinally(signalType -> MDC.remove(CORRELATION_ID_KEY));
    }
}
