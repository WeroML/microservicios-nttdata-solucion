package tacos.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * TC-09/TC-11: los 401 y 403 que genera el filtro de seguridad usan la misma
 * estructura que ApiProblem (type, title, status, detail, instance, code).
 */
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String realmName;

  public ProblemSecurityHandler(String realmName) {
    this.realmName = realmName;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    response.addHeader("WWW-Authenticate", "Basic realm=\"" + realmName + "\"");
    write(request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
        "Authentication is required to access this resource.");
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
        "You are not allowed to access this resource.");
  }

  private void write(HttpServletRequest request, HttpServletResponse response,
      HttpStatus status, String code, String detail) throws IOException {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", "about:blank");
    problem.put("title", status.getReasonPhrase());
    problem.put("status", status.value());
    problem.put("detail", detail);
    problem.put("instance", request.getRequestURI());
    problem.put("code", code);
    Object correlationId = request.getAttribute("correlationId");
    if (correlationId != null) {
      problem.put("correlationId", correlationId);
    }

    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    MAPPER.writeValue(response.getOutputStream(), problem);
  }
}
