package tacos.web.api;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class ApiVersioningFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // If they use the legacy /api path (but not /api/v1)
        if (path.startsWith("/api/") && !path.startsWith("/api/v1/")) {
            exchange.getResponse().getHeaders().add("Warning", "299 - \"Deprecated API: Please migrate to /api/v1\"");
            
            // To seamlessly support it without rewriting all controllers, we could rewrite the request path
            // But actually we just map controllers to both, or we can use ServerWebExchange mutation.
            ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .path(path.replaceFirst("/api/", "/api/v1/"))
                    .build())
                .build();
            return chain.filter(mutated);
        }
        
        return chain.filter(exchange);
    }
}
