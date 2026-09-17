package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.domain.model.Role;

import java.util.Optional;

public interface RoleRepository {
    Optional<Role> findByName(String name);
}
