package tacos.validation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tacos.Taco;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@RestController
@RequestMapping(path="/api/tacos/validate", produces="application/json")
public class TacoValidationController {

    private final TacoValidatorService validatorService;

    public TacoValidationController(TacoValidatorService validatorService) {
        this.validatorService = validatorService;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, List<String>>>> validateTaco(@RequestBody Taco taco) {
        List<String> errors = validatorService.validate(taco);
        
        if (errors.isEmpty()) {
            return Mono.just(ResponseEntity.ok(Collections.singletonMap("errors", Collections.emptyList())));
        } else {
            // "Retornar violaciones ordenadas en /api/tacos/validate impidiendo guardado inválido."
            // We return 400 Bad Request or 422 Unprocessable Entity with the errors.
            return Mono.just(ResponseEntity.unprocessableEntity().body(Collections.singletonMap("errors", errors)));
        }
    }
}
