package tacos.api.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String code, String detail) {
    super(HttpStatus.NOT_FOUND, code, detail);
  }
}
