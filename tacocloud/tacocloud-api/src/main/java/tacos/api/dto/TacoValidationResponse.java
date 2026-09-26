package tacos.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.validation.RuleViolation;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacoValidationResponse {
    private boolean valid;
    private List<RuleViolation> violations;
}
