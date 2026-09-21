package tacos.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation
             .authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web
             .builders.HttpSecurity;
import org.springframework.security.config.annotation.web
                        .configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web
                        .configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("deprecation")
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
  
  @Autowired
  private UserDetailsService userDetailsService;
  
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http
      .authorizeRequests()
        .antMatchers(HttpMethod.OPTIONS).permitAll() // needed for Angular/CORS
        .antMatchers(HttpMethod.GET, "/api/**tacos/**", "/api/**ingredients/**").permitAll()
        .antMatchers("/api/**ingredients/**").hasRole("ADMIN")
        .antMatchers("/api/**kitchen/**").hasRole("KITCHEN")
        .antMatchers("/api/**orders/**").hasRole("USER")
        .antMatchers("/actuator/health").permitAll()
        .antMatchers("/actuator/**").hasRole("ADMIN")
        .antMatchers("/data-api/**").hasRole("ADMIN")
        .antMatchers("/register", "/login").permitAll()
        .antMatchers("/", "/**/*.html", "/**/*.js", "/**/*.css", "/**/*.ico").permitAll()
        .anyRequest().authenticated()
        
      .and()
        .formLogin()
          .loginPage("/login")
          
      .and()
        .httpBasic()
          .realmName("Taco Cloud")
          
      .and()
        .logout()
          .logoutSuccessUrl("/")
          
      .and()
        .csrf()
          .ignoringAntMatchers("/h2-console/**", "/api/**", "/api/v1/**")

      // Allow pages to be loaded in frames from the same origin; needed for H2-Console
      .and()  
        .headers()
          .frameOptions()
            .sameOrigin()
      ;
  }

  @Bean
  public PasswordEncoder encoder() {
    return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
  
  
  @Override
  protected void configure(AuthenticationManagerBuilder auth)
      throws Exception {

    auth
      .userDetailsService(userDetailsService)
      .passwordEncoder(encoder());
    
  }

}
