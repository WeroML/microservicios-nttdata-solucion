package tacos.web.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import tacos.api.dto.ApiProblem;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiProblem> handleValidationException(WebExchangeBindException ex, ServerWebExchange exchange) {
        ApiProblem problem = ApiProblem.builder()
                .type("about:blank")
                .title("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Validation failed")
                .instance(exchange.getRequest().getPath().value())
                .code("VALIDATION_ERROR")
                .violations(ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> ApiProblem.Violation.builder()
                                .field(fe.getField())
                                .message(fe.getDefaultMessage())
                                .build())
                        .collect(Collectors.toList()))
                .build();
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiProblem> handleIllegalArgument(IllegalArgumentException ex, ServerWebExchange exchange) {
        ApiProblem problem = ApiProblem.builder()
                .type("about:blank")
                .title("Unprocessable Entity")
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .detail(ex.getMessage())
                .instance(exchange.getRequest().getPath().value())
                .code("INVALID_ARGUMENT")
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiProblem> handleIllegalState(IllegalStateException ex, ServerWebExchange exchange) {
        ApiProblem problem = ApiProblem.builder()
                .type("about:blank")
                .title("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .detail(ex.getMessage())
                .instance(exchange.getRequest().getPath().value())
                .code("CONFLICT")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiProblem> handleOptimisticLockingFailure(org.springframework.dao.OptimisticLockingFailureException ex, ServerWebExchange exchange) {
        ApiProblem problem = ApiProblem.builder()
                .type("about:blank")
                .title("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .detail("The resource was modified concurrently. Please reload and try again.")
                .instance(exchange.getRequest().getPath().value())
                .code("CONCURRENT_MODIFICATION")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
