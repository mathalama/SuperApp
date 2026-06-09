package dev.mathalama.identityservice.domain.port.in;

import dev.mathalama.identityservice.domain.model.OAuthProvider;
import dev.mathalama.identityservice.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OAuthProviderUseCase {
    Optional<User> findUserByOAuthProvider(String providerName, String providerId);
    OAuthProvider linkOAuthProvider(User user, String providerName, String providerId, String providerEmail);
    boolean isProviderLinked(User user, String providerName);
    List<OAuthProvider> getUserProviders(User user);
    List<String> getLinkedProviderNames(UUID userId);
    void recordLogin(User user, String providerName);
    boolean unlinkOAuthProvider(UUID userId, String providerName);
    boolean providerIdExists(String providerName, String providerId);
    Optional<OAuthProvider> findByProviderEmail(String providerEmail);
    long countLinkedProviders(UUID userId);
}
