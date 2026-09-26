package tacos.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TC-18: violación con código estable para la UI y mensaje legible.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleViolation {
    private String code;
    private String message;
}
