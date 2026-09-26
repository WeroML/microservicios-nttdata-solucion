package tacos.api.error;

import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;

import tacos.api.dto.ApiProblem;

/**
 * TC-09: error funcional con estado HTTP y código estable para la UI.
 *
 * Política del curso:
 * - 400: la petición está mal formada o no cumple el formato (Bean Validation, IDs inconsistentes).
 * - 404: el recurso no existe o no pertenece al usuario (no se revela si existe).
 * - 409: conflicto con el estado actual (versión, duplicado, transición, stock, idempotencia).
 * - 422: la petición es válida pero viola una regla de negocio (diseño de taco, cupón, cantidad).
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String code;
  private final transient List<ApiProblem.Violation> violations;

  public ApiException(HttpStatus status, String code, String detail) {
    this(status, code, detail, Collections.emptyList());
  }

  public ApiException(HttpStatus status, String code, String detail, List<ApiProblem.Violation> violations) {
    super(detail);
    this.status = status;
    this.code = code;
    this.violations = violations;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public List<ApiProblem.Violation> getViolations() {
    return violations;
  }
}
