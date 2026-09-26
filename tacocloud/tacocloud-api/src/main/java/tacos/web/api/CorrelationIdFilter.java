package tacos.web.api;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import reactor.util.context.Context;

/**
 * TC-31: X-Correlation-Id de entrada/salida.
 *
 * Estrategia (el runtime es Spring MVC):
 * - En el borde MVC este filtro valida o genera el ID, lo pone en MDC, en un
 *   atributo del request y en el header de respuesta; al terminar limpia el MDC.
 * - MDC es ThreadLocal y no viaja solo por los operadores de Reactor. Por eso
 *   el controlador copia el ID al Reactor Context con {@link #reactorContext()}
 *   y la fábrica de eventos lo lee de ahí (paso explícito).
 * - En procesos asíncronos (outbox, listeners) el ID viaja dentro del evento y
 *   se vuelve a poner en MDC sólo mientras se procesa ese evento.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";

    // Sólo caracteres seguros y longitud acotada: evita inyección de líneas en logs.
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String correlationId = (String) request.getAttribute(CORRELATION_ID_KEY);
        if (correlationId == null) {
            correlationId = resolve(request.getHeader(CORRELATION_ID_HEADER));
            request.setAttribute(CORRELATION_ID_KEY, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        MDC.put(CORRELATION_ID_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    // También corre en el dispatch asíncrono que usa MVC al terminar un Mono.
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    static String resolve(String headerValue) {
        if (headerValue != null && VALID_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    // Se llama en el hilo del request (donde el MDC sí tiene el valor).
    public static Context reactorContext() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        return correlationId == null ? Context.empty() : Context.of(CORRELATION_ID_KEY, correlationId);
    }
}
