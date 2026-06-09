package dev.mathalama.identityservice.infrastructure.persistence.jpa;

import dev.mathalama.identityservice.domain.model.OAuthProvider;
import dev.mathalama.identityservice.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaOAuthProviderRepository extends JpaRepository<OAuthProvider, UUID> {
    Optional<OAuthProvider> findByProviderNameAndProviderId(String providerName, String providerId);
    List<OAuthProvider> findByUser(User user);
    Optional<OAuthProvider> findByUserAndProviderName(User user, String providerName);
    Optional<OAuthProvider> findByUser_IdAndProviderName(UUID userId, String providerName);
    
    @Query("SELECT o.providerName FROM OAuthProvider o WHERE o.user.id = :userId")
    List<String> findProviderNamesByUserId(@Param("userId") UUID userId);
    
    long countByUser_Id(UUID userId);
    void deleteByUser_IdAndProviderName(UUID userId, String providerName);
}
