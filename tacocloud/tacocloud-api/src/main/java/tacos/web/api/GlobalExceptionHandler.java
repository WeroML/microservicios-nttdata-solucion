package tacos.web.api;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import tacos.api.dto.ApiProblem;
import tacos.api.error.ApiException;

/**
 * TC-09: todas las respuestas 4xx/5xx controladas usan ApiProblem
 * (application/problem+json). Nunca se devuelven stack traces ni mensajes
 * del driver; los errores inesperados se registran y responden 500 genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiProblem> handleApiException(ApiException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getViolations(), request);
    }

    @ExceptionHandler(BindException.class) // incluye MethodArgumentNotValidException
    public ResponseEntity<ApiProblem> handleValidation(BindException ex, HttpServletRequest request) {
        List<ApiProblem.Violation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> ApiProblem.Violation.builder()
                .field(fe.getField())
                .code(fe.getCode())
                .message(fe.getDefaultMessage())
                .build())
            .collect(Collectors.toList());
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiProblem> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiProblem.Violation> violations = ex.getConstraintViolations().stream()
            .map(v -> ApiProblem.Violation.builder()
                .field(v.getPropertyPath().toString())
                .message(v.getMessage())
                .build())
            .collect(Collectors.toList());
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", violations, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiProblem> handleMalformed(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be read.",
            Collections.emptyList(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiProblem> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed.",
            Collections.emptyList(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiProblem> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type.",
            Collections.emptyList(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiProblem> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
            "The resource was modified concurrently. Reload and try again.", Collections.emptyList(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiProblem> handleDuplicate(DuplicateKeyException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "The resource already exists.",
            Collections.emptyList(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiProblem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You are not allowed to access this resource.",
            Collections.emptyList(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiProblem> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String code = ex.getReason() != null ? ex.getReason() : ex.getStatus().name();
        return problem(ex.getStatus(), code, ex.getStatus().getReasonPhrase(), Collections.emptyList(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error.",
            Collections.emptyList(), request);
    }

    private ResponseEntity<ApiProblem> problem(HttpStatus status, String code, String detail,
            List<ApiProblem.Violation> violations, HttpServletRequest request) {
        ApiProblem problem = ApiProblem.builder()
            .type("about:blank")
            .title(status.getReasonPhrase())
            .status(status.value())
            .detail(detail)
            .instance(request.getRequestURI())
            .code(code)
            .correlationId((String) request.getAttribute(CorrelationIdFilter.CORRELATION_ID_KEY))
            .violations(violations)
            .build();
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(problem);
    }
}
