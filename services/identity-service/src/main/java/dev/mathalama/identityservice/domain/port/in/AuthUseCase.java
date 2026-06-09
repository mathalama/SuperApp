package dev.mathalama.identityservice.domain.port.in;

import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.application.dto.request.SignInRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface AuthUseCase {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
    void register(String username, String email, String password);
    User authenticate(SignInRequest request);
    void logout(String userId, String accessToken);
    void assignRoleToUser(String username, String roleName);
}
