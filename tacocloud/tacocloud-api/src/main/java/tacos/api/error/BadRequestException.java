package tacos.api.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

  private static final long serialVersionUID = 1L;

  public BadRequestException(String code, String detail) {
    super(HttpStatus.BAD_REQUEST, code, detail);
  }
}
