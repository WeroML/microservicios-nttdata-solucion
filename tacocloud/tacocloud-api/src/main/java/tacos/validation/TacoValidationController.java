package tacos.validation;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import tacos.api.dto.TacoRequest;
import tacos.api.dto.TacoValidationResponse;

@RestController
@RequestMapping(path={"/api/v1/tacos/validate", "/api/tacos/validate"}, produces="application/json")
public class TacoValidationController {

    private final TacoDesignService designService;

    public TacoValidationController(TacoDesignService designService) {
        this.designService = designService;
    }

    // 200 si el diseño es válido; 422 con todas las violaciones si no. Nunca guarda.
    @PostMapping(consumes="application/json")
    public Mono<ResponseEntity<TacoValidationResponse>> validateTaco(@Valid @RequestBody TacoRequest request) {
        return designService.validate(request)
            .map(this::toResponse);
    }

    private ResponseEntity<TacoValidationResponse> toResponse(List<RuleViolation> violations) {
        if (violations.isEmpty()) {
            return ResponseEntity.ok(new TacoValidationResponse(true, violations));
        }
        return ResponseEntity.unprocessableEntity().body(new TacoValidationResponse(false, violations));
    }
}
