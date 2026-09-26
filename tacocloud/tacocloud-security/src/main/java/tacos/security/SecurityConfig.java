package tacos.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation
             .authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web
             .builders.HttpSecurity;
import org.springframework.security.config.annotation.web
                        .configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web
                        .configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * TC-11: matriz de autorización (deny-by-default).
 *
 * <pre>
 * Ruta (/api/v1/... y alias legado /api/...)          Acceso
 * ---------------------------------------------------  ---------------------
 * GET  ingredients, tacos, tacos/**                    público (catálogo)
 * POST tacos/validate                                  público
 * POST tacos, PUT tacos/{id}/rating                    USER
 * POST orders, orders/quote, orders/{id}/...            USER o ADMIN (+ ownership en servicio)
 * PATCH orders/{id}/status                             KITCHEN o ADMIN
 * POST orders/fromEmail                                ADMIN (cuenta de integración)
 * users/me/**, payment-methods/**, coupons/**          USER
 * GET  announcements                                   autenticado
 * POST/PUT/DELETE ingredients, admin/**                ADMIN
 * kitchen/**                                           KITCHEN
 * cualquier otra ruta /api/**                          denegada
 * /actuator/health/**                                  público (detalles sólo ADMIN)
 * /actuator/**, /data-api/**                           ADMIN
 * rutas de la UI (Angular) y archivos estáticos        público
 * cualquier otra ruta                                  denegada
 * </pre>
 *
 * CSRF: /api/** usa HTTP Basic (credencial en cada petición, no cookie),
 * por eso se excluye de CSRF. La cookie de sesión de la UI es SameSite=Strict
 * (application.yml), lo que impide que otro sitio la use contra la API.
 * Los formularios /login y /register sí conservan CSRF.
 */
@SuppressWarnings("deprecation")
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

  private static final String USER = "USER";
  private static final String ADMIN = "ADMIN";
  private static final String KITCHEN = "KITCHEN";

  @Autowired
  private UserDetailsService userDetailsService;

  @Value("${tacocloud.cors.allowed-origins:}")
  private List<String> allowedOrigins;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    ProblemSecurityHandler problemHandler = new ProblemSecurityHandler("Taco Cloud");

    http
      .cors()

      .and()
      .authorizeRequests()
        .antMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()

        // Catálogo público
        .antMatchers(HttpMethod.GET, api("/ingredients", "/ingredients/**", "/tacos", "/tacos/**")).permitAll()
        .antMatchers(HttpMethod.POST, api("/tacos/validate")).permitAll()

        // Administración
        .antMatchers(api("/admin/**")).hasRole(ADMIN)
        .antMatchers(HttpMethod.POST, api("/ingredients")).hasRole(ADMIN)
        .antMatchers(HttpMethod.PUT, api("/ingredients/*")).hasRole(ADMIN)
        .antMatchers(HttpMethod.DELETE, api("/ingredients/*")).hasRole(ADMIN)

        // Cocina
        .antMatchers(api("/kitchen/**")).hasRole(KITCHEN)
        .antMatchers(HttpMethod.PATCH, api("/orders/*/status")).hasAnyRole(KITCHEN, ADMIN)

        // Integración de correo
        .antMatchers(HttpMethod.POST, api("/orders/fromEmail")).hasRole(ADMIN)

        // Cliente
        .antMatchers(HttpMethod.POST, api("/tacos")).hasRole(USER)
        .antMatchers(HttpMethod.PUT, api("/tacos/*/rating")).hasRole(USER)
        .antMatchers(api("/orders", "/orders/**")).hasAnyRole(USER, ADMIN)
        .antMatchers(api("/users/me/**", "/payment-methods/**", "/coupons/**")).hasRole(USER)
        .antMatchers(HttpMethod.GET, api("/announcements")).authenticated()

        // Una ruta nueva de la API queda negada hasta que se agregue a la matriz.
        .antMatchers("/api/**").denyAll()

        // Operación
        .antMatchers("/actuator/health", "/actuator/health/**").permitAll()
        .antMatchers("/actuator/**").hasRole(ADMIN)
        .antMatchers("/data-api/**").hasRole(ADMIN)

        // UI y formularios
        .antMatchers("/register", "/login", "/logout", "/error").permitAll()
        .antMatchers(HttpMethod.GET, "/openapi.yaml").permitAll()
        .antMatchers(HttpMethod.GET, "/", "/index.html", "/*.js", "/*.css", "/*.ico",
            "/*.woff2", "/*.woff", "/*.ttf", "/*.svg", "/*.png", "/*.jpg", "/*.map", "/assets/**",
            "/home", "/recents", "/specials", "/locations", "/design", "/cart",
            "/favorites", "/orders-history").permitAll()
        .anyRequest().denyAll()

      .and()
        .exceptionHandling()
          .defaultAuthenticationEntryPointFor(problemHandler, new AntPathRequestMatcher("/api/**"))
          .defaultAccessDeniedHandlerFor(problemHandler, new AntPathRequestMatcher("/api/**"))

      .and()
        .formLogin()
          .loginPage("/login")

      .and()
        .httpBasic()
          .realmName("Taco Cloud")
          .authenticationEntryPoint(problemHandler)

      .and()
        .logout()
          .logoutSuccessUrl("/")

      .and()
        .csrf()
          .ignoringAntMatchers("/api/**")
      ;
  }

  // Cada regla aplica igual a /api/v1 y al alias legado /api (TC-35).
  private static String[] api(String... paths) {
    String[] result = new String[paths.length * 2];
    for (int i = 0; i < paths.length; i++) {
      result[i * 2] = "/api/v1" + paths[i];
      result[i * 2 + 1] = "/api" + paths[i];
    }
    return result;
  }

  // TC-11: CORS con orígenes explícitos tomados de configuración, sin comodín.
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept",
        "Idempotency-Key", "X-Correlation-Id"));
    config.setExposedHeaders(List.of("Location", "X-Correlation-Id", "Deprecation", "Link"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }

  // TC-10: hash adaptativo con identificador de algoritmo ({bcrypt}...).
  @Bean
  public PasswordEncoder encoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth)
      throws Exception {

    auth
      .userDetailsService(userDetailsService)
      .passwordEncoder(encoder());

  }

}
