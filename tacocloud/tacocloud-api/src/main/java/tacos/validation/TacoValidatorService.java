package tacos.validation;

import org.springframework.stereotype.Service;
import tacos.Taco;
import java.util.List;
import java.util.ArrayList;

@Service
public class TacoValidatorService {

    private final List<TacoRule> rules;

    public TacoValidatorService(List<TacoRule> rules) {
        this.rules = rules;
    }

    public List<String> validate(Taco taco) {
        List<String> errors = new ArrayList<>();
        for (TacoRule rule : rules) {
            errors.addAll(rule.validate(taco));
        }
        return errors;
    }
}
