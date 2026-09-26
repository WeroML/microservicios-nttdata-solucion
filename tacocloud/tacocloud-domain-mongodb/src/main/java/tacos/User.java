package tacos;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.
                                          SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor(access=AccessLevel.PRIVATE, force=true)
// Spring Data debe leer el usuario con este constructor (los campos son final).
@RequiredArgsConstructor(onConstructor_ = @PersistenceConstructor)
@Document
public class User implements UserDetails {

  private static final long serialVersionUID = 1L;

  public static final String ROLE_USER = "USER";
  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_KITCHEN = "KITCHEN";

  @Id
  private String id;

  // TC-10: la unicidad no depende sólo de "consultar antes"; el índice
  // único protege contra registros concurrentes.
  @Indexed(unique = true)
  private final String username;

  private final String password;
  private final String fullname;
  private final String street;
  private final String city;
  private final String state;
  private final String zip;
  private final String phoneNumber;

  @Indexed(unique = true)
  private final String email;

  // TC-11: roles USER, ADMIN y KITCHEN. Todo usuario registrado es USER.
  private Set<String> roles = new HashSet<>(Collections.singleton(ROLE_USER));

  @Override
  public String getUsername() {
    return this.username;
  }

  @Override
  public String getPassword() {
    return this.password;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .collect(Collectors.toList());
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

}
