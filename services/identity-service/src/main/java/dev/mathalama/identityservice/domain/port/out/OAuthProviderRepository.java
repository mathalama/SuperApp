package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.domain.model.OAuthProvider;
import dev.mathalama.identityservice.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthProviderRepository {
    Optional<OAuthProvider> findByProviderNameAndProviderId(String providerName, String providerId);
    List<OAuthProvider> findByUser(User user);
    Optional<OAuthProvider> findByUserAndProviderName(User user, String providerName);
    Optional<OAuthProvider> findByUserIdAndProviderName(UUID userId, String providerName);
    List<String> findProviderNamesByUserId(UUID userId);
    long countByUserId(UUID userId);
    void deleteByUserIdAndProviderName(UUID userId, String providerName);
    OAuthProvider save(OAuthProvider provider);
}
