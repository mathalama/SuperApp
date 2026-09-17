package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    User save(User user);
}
