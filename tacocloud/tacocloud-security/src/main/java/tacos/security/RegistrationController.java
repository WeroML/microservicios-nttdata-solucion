package tacos.security;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tacos.data.UserRepository;

import reactor.core.publisher.Mono;

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
  
  @PostMapping
  public Mono<String> processRegistration(RegistrationForm form) {
    return userRepo.findByUsername(form.getUsername())
        .flatMap(existing -> Mono.<String>error(new IllegalStateException("Username already exists")))
        .switchIfEmpty(
            userRepo.save(form.toUser(passwordEncoder))
                .map(user -> "redirect:/login")
        );
  }

}
