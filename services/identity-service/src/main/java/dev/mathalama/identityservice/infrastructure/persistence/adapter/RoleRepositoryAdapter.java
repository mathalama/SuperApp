package dev.mathalama.identityservice.infrastructure.persistence.adapter;

import dev.mathalama.identityservice.domain.model.Role;
import dev.mathalama.identityservice.domain.port.out.RoleRepository;
import dev.mathalama.identityservice.infrastructure.persistence.jpa.JpaRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {
    private final JpaRoleRepository jpa;

    @Override
    public Optional<Role> findByName(String name) { return jpa.findByName(name); }
}
