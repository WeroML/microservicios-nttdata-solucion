package tacos.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import tacos.Taco;

/**
 * TC-18: ejecuta la colección inyectada de reglas. Agregar una regla nueva sólo
 * requiere crear otro @Component que implemente TacoRule.
 * No es fail-fast: se evalúan todas las reglas y el resultado se ordena por
 * código, así que el orden de las reglas no cambia el resultado.
 */
@Service
public class TacoValidatorService {

    private final List<TacoRule> rules;

    public TacoValidatorService(List<TacoRule> rules) {
        this.rules = rules;
    }

    public List<RuleViolation> validate(Taco taco) {
        List<RuleViolation> violations = new ArrayList<>();
        for (TacoRule rule : rules) {
            violations.addAll(rule.validate(taco));
        }
        violations.sort(Comparator.comparing(RuleViolation::getCode)
            .thenComparing(RuleViolation::getMessage));
        return violations;
    }
}
