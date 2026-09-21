package tacos.api.dto;

import java.util.List;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ApiProblem {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String code;
    private List<Violation> violations;

    @Data
    @Builder
    public static class Violation {
        private String field;
        private String message;
    }
}
