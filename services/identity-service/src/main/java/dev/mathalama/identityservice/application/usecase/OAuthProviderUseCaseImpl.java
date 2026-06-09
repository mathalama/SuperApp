package dev.mathalama.identityservice.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mathalama.identityservice.domain.model.OAuthProvider;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.OAuthProviderUseCase;
import dev.mathalama.identityservice.domain.port.out.OAuthProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OAuthProviderUseCaseImpl implements OAuthProviderUseCase {

    private final OAuthProviderRepository oauthProviderRepository;

    @Override
    public Optional<User> findUserByOAuthProvider(String providerName, String providerId) {
        log.debug("Looking up user by {} provider with ID: {}", providerName, providerId);
        
        return oauthProviderRepository
                .findByProviderNameAndProviderId(providerName, providerId)
                .map(OAuthProvider::getUser);
    }

    @Override
    public OAuthProvider linkOAuthProvider(User user, String providerName, String providerId, String providerEmail) {
        log.info("Linking {} provider to user: {}", providerName, user.getUsername());
        
        Optional<OAuthProvider> existing = oauthProviderRepository
                .findByUserAndProviderName(user, providerName);
        
        if (existing.isPresent()) {
            log.warn("Provider {} already linked to user {}", providerName, user.getUsername());
            OAuthProvider provider = existing.get();
            provider.setUpdatedAt(Instant.now());
            return oauthProviderRepository.save(provider);
        }

        OAuthProvider provider = new OAuthProvider(user, providerName, providerId, providerEmail);
        return oauthProviderRepository.save(provider);
    }

    @Override
    public boolean isProviderLinked(User user, String providerName) {
        return oauthProviderRepository.findByUserAndProviderName(user, providerName).isPresent();
    }

    @Override
    public List<OAuthProvider> getUserProviders(User user) {
        return oauthProviderRepository.findByUser(user);
    }

    @Override
    public List<String> getLinkedProviderNames(UUID userId) {
        return oauthProviderRepository.findProviderNamesByUserId(userId);
    }

    @Override
    public void recordLogin(User user, String providerName) {
        oauthProviderRepository
                .findByUserAndProviderName(user, providerName)
                .ifPresent(provider -> {
                    provider.recordLogin();
                    oauthProviderRepository.save(provider);
                    log.debug("Recorded login for user {} via {}", user.getUsername(), providerName);
                });
    }

    @Override
    public boolean unlinkOAuthProvider(UUID userId, String providerName) {
        Optional<OAuthProvider> provider = oauthProviderRepository.findByUserIdAndProviderName(userId, providerName);
        
        if (provider.isPresent()) {
            oauthProviderRepository.deleteByUserIdAndProviderName(userId, providerName);
            log.info("Unlinked {} provider from user ID: {}", providerName, userId);
            return true;
        }
        
        log.warn("Could not unlink {} provider - not found for user ID: {}", providerName, userId);
        return false;
    }

    @Override
    public boolean providerIdExists(String providerName, String providerId) {
        return oauthProviderRepository
                .findByProviderNameAndProviderId(providerName, providerId)
                .isPresent();
    }

    @Override
    public Optional<OAuthProvider> findByProviderEmail(String providerEmail) {
        return Optional.empty();
    }

    @Override
    public long countLinkedProviders(UUID userId) {
        return oauthProviderRepository.countByUserId(userId);
    }
}
