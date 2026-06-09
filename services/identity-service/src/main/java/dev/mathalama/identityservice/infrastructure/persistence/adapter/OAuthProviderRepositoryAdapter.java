package dev.mathalama.identityservice.infrastructure.persistence.adapter;

import dev.mathalama.identityservice.domain.model.OAuthProvider;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.out.OAuthProviderRepository;
import dev.mathalama.identityservice.infrastructure.persistence.jpa.JpaOAuthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OAuthProviderRepositoryAdapter implements OAuthProviderRepository {
    private final JpaOAuthProviderRepository jpa;

    @Override
    public Optional<OAuthProvider> findByProviderNameAndProviderId(String providerName, String providerId) {
        return jpa.findByProviderNameAndProviderId(providerName, providerId);
    }

    @Override
    public List<OAuthProvider> findByUser(User user) {
        return jpa.findByUser(user);
    }

    @Override
    public Optional<OAuthProvider> findByUserAndProviderName(User user, String providerName) {
        return jpa.findByUserAndProviderName(user, providerName);
    }

    @Override
    public Optional<OAuthProvider> findByUserIdAndProviderName(UUID userId, String providerName) {
        return jpa.findByUser_IdAndProviderName(userId, providerName);
    }

    @Override
    public List<String> findProviderNamesByUserId(UUID userId) {
        return jpa.findProviderNamesByUserId(userId);
    }

    @Override
    public long countByUserId(UUID userId) {
        return jpa.countByUser_Id(userId);
    }

    @Override
    public void deleteByUserIdAndProviderName(UUID userId, String providerName) {
        jpa.deleteByUser_IdAndProviderName(userId, providerName);
    }

    @Override
    public OAuthProvider save(OAuthProvider provider) {
        return jpa.save(provider);
    }
}
