package tacos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.User;
import tacos.data.UserRepository;

// TC-10: registro reactivo con contraseñas protegidas.
class RegistrationControllerTest {

  private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  private UserRepository userRepo;
  private RegistrationController controller;

  @BeforeEach
  void setUp() {
    userRepo = mock(UserRepository.class);
    controller = new RegistrationController(userRepo, encoder);
    when(userRepo.findByUsername(any())).thenReturn(Mono.empty());
    when(userRepo.findByEmail(any())).thenReturn(Mono.empty());
  }

  private static RegistrationForm form() {
    RegistrationForm form = new RegistrationForm();
    form.setUsername("newuser");
    form.setPassword("s3cret-password");
    form.setFullname("New User");
    form.setEmail("new@tacocloud.test");
    return form;
  }

  @Test
  void encoderUsesAdaptiveHashWithAlgorithmId() {
    String hash = encoder.encode("s3cret-password");

    assertThat(hash).startsWith("{bcrypt}");
    assertThat(hash).doesNotContain("s3cret-password");
    assertThat(encoder.matches("s3cret-password", hash)).isTrue();
  }

  @Test
  void savesHashedPasswordAndRedirectsOnlyAfterSaveCompletes() {
    AtomicInteger saveSubscriptions = new AtomicInteger();
    when(userRepo.save(any())).thenAnswer(inv -> Mono.defer(() -> {
      saveSubscriptions.incrementAndGet();
      return Mono.just((User) inv.getArgument(0));
    }));

    Mono<String> result = controller.processRegistration(form());
    // Publisher frío: nada se guarda hasta que el framework suscribe.
    assertThat(saveSubscriptions.get()).isZero();

    StepVerifier.create(result).expectNext("redirect:/login").verifyComplete();
    assertThat(saveSubscriptions.get()).isEqualTo(1);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepo).save(saved.capture());
    assertThat(saved.getValue().getPassword()).isNotEqualTo("s3cret-password");
    assertThat(encoder.matches("s3cret-password", saved.getValue().getPassword())).isTrue();
    assertThat(saved.getValue().getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  void duplicateUsernameOrEmailIsConflictAndNothingIsSaved() {
    when(userRepo.findByEmail("new@tacocloud.test")).thenReturn(Mono.just(mock(User.class)));

    StepVerifier.create(controller.processRegistration(form()))
        .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
        .verify();
    verify(userRepo, never()).save(any());
  }

  // Carrera: ambos "consultan antes" y pasan; el índice único rechaza al segundo.
  @Test
  void duplicateKeyFromUniqueIndexIsConflict() {
    when(userRepo.save(any())).thenReturn(Mono.error(new DuplicateKeyException("E11000")));

    StepVerifier.create(controller.processRegistration(form()))
        .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
        .verify();
  }

  @Test
  void formNeverPrintsThePassword() {
    assertThat(form().toString()).doesNotContain("s3cret-password");
  }
}
