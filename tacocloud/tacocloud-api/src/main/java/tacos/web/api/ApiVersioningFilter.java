package tacos.web.api;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TC-35: /api/v1 es el contrato público. El alias legado /api sigue atendido por
 * los mismos controladores (y la misma matriz de seguridad), pero cada respuesta
 * lo marca como obsoleto e indica la ruta sucesora.
 */
@Component
public class ApiVersioningFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/") && !path.startsWith("/api/v1/")) {
            String successor = request.getContextPath() + "/api/v1/" + path.substring("/api/".length());
            response.setHeader("Deprecation", "true");
            response.setHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
            response.setHeader("Warning", "299 - \"Deprecated API: migrate to /api/v1\"");
        }
        chain.doFilter(request, response);
    }
}
