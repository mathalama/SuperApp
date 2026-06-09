package dev.mathalama.identityservice.infrastructure.persistence.adapter;

import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import dev.mathalama.identityservice.infrastructure.persistence.jpa.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {
    private final JpaUserRepository jpa;

    @Override
    public Optional<User> findById(UUID id) { return jpa.findById(id); }
    @Override
    public Optional<User> findByUsername(String username) { return jpa.findByUsername(username); }
    @Override
    public Optional<User> findByEmail(String email) { return jpa.findByEmail(email); }
    @Override
    public Optional<User> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username) {
        return jpa.findByEmailIgnoreCaseOrUsernameIgnoreCase(email, username);
    }
    @Override
    public boolean existsByUsername(String username) { return jpa.existsByUsername(username); }
    @Override
    public boolean existsByEmail(String email) { return jpa.existsByEmail(email); }
    @Override
    public User save(User user) { return jpa.save(user); }
}
