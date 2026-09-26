package tacos.api.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String code, String detail) {
    super(HttpStatus.CONFLICT, code, detail);
  }
}
