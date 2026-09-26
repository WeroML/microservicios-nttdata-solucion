package tacos.security;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.Data;
import tacos.User;

@Data
public class RegistrationForm {

  @NotBlank
  @Size(max = 50)
  private String username;
  @NotBlank
  @Size(min = 8, max = 100)
  private String password;
  @NotBlank
  private String fullname;
  private String street;
  private String city;
  private String state;
  private String zip;
  private String phone;
  @NotBlank
  @Email
  private String email;

  public User toUser(PasswordEncoder passwordEncoder) {
    return new User(
        username, passwordEncoder.encode(password), 
        fullname, street, city, state, zip, phone, email);
  }

  // TC-10: la contraseña nunca aparece en logs.
  @Override
  public String toString() {
    return "RegistrationForm(username=" + username + ", email=" + email + ")";
  }

}
