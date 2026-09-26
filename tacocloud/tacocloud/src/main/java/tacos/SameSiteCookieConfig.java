package tacos;

import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * TC-11: la cookie de sesión de la UI es SameSite=Strict, así otro sitio no puede
 * usarla contra la API (que no usa token CSRF porque se autentica con HTTP Basic).
 * Spring Boot 2.5 aún no tiene propiedad para esto, por eso se configura en Tomcat.
 */
@Configuration
public class SameSiteCookieConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    factory.addContextCustomizers(context -> {
      Rfc6265CookieProcessor cookieProcessor = new Rfc6265CookieProcessor();
      cookieProcessor.setSameSiteCookies("strict");
      context.setCookieProcessor(cookieProcessor);
    });
  }
}
