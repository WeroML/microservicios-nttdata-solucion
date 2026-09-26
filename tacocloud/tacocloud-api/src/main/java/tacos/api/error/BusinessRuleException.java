package tacos.api.error;

import java.util.List;

import org.springframework.http.HttpStatus;

import tacos.api.dto.ApiProblem;

public class BusinessRuleException extends ApiException {

  private static final long serialVersionUID = 1L;

  public BusinessRuleException(String code, String detail) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
  }

  public BusinessRuleException(String code, String detail, List<ApiProblem.Violation> violations) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, code, detail, violations);
  }
}
