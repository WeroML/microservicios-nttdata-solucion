package tacos.api.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

  private static final long serialVersionUID = 1L;

  public ForbiddenException(String code, String detail) {
    super(HttpStatus.FORBIDDEN, code, detail);
  }
}
