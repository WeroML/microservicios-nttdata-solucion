package tacos.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * TC-09: estructura de error compatible conceptualmente con RFC 9457.
 * Spring 5.3 no tiene ProblemDetail, por eso es un DTO propio.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiProblem {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String code;
    private String correlationId;
    private List<Violation> violations;

    @Data
    @Builder
    public static class Violation {
        private String field;
        private String code;
        private String message;
    }
}
