package tacos.security;

import javax.validation.Valid;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import tacos.data.UserRepository;

@Controller
@RequestMapping("/register")
public class RegistrationController {

  private UserRepository userRepo;
  private PasswordEncoder passwordEncoder;

  public RegistrationController(
      UserRepository userRepo, PasswordEncoder passwordEncoder) {
    this.userRepo = userRepo;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping
  public String registerForm() {
    return "registration";
  }

  /**
   * TC-10: el guardado forma parte de la cadena retornada; la redirección
   * ocurre sólo después de que Mongo confirmó la escritura.
   * La unicidad se revisa antes de guardar y además la garantiza el índice
   * único; si dos registros compiten, DuplicateKeyException también da 409.
   */
  @PostMapping
  public Mono<String> processRegistration(@Valid RegistrationForm form) {
    return Mono.zip(
            userRepo.findByUsername(form.getUsername()).hasElement(),
            userRepo.findByEmail(form.getEmail()).hasElement())
        .flatMap(taken -> {
          if (taken.getT1() || taken.getT2()) {
            return Mono.error(duplicateUser());
          }
          return Mono.defer(() -> userRepo.save(form.toUser(passwordEncoder)));
        })
        .onErrorMap(DuplicateKeyException.class, e -> duplicateUser())
        .thenReturn("redirect:/login");
  }

  private static ResponseStatusException duplicateUser() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_OR_EMAIL_TAKEN");
  }

}
