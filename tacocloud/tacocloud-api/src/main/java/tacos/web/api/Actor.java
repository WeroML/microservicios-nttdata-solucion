package tacos.web.api;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import tacos.User;

/**
 * Identidad de quien ejecuta la operación, tomada siempre de la autenticación
 * (nunca de un userId enviado por el cliente).
 */
public final class Actor {

    private final String userId;
    private final String username;
    private final Set<String> roles;

    public Actor(String userId, String username, Set<String> roles) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
    }

    public static Actor from(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .collect(Collectors.toSet());
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            User user = (User) principal;
            return new Actor(user.getId(), user.getUsername(), roles);
        }
        return new Actor(authentication.getName(), authentication.getName(), roles);
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(User.ROLE_ADMIN);
    }
}
