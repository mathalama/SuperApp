package dev.mathalama.identityservice.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "oauth_providers",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "provider_name"}, name = "unique_provider_per_user")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "provider_id", nullable = false, length = 500)
    private String providerId;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public OAuthProvider(User user, String providerName, String providerId, String providerEmail) {
        this.user = user;
        this.providerName = providerName;
        this.providerId = providerId;
        this.providerEmail = providerEmail;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isProvider(String name) {
        return this.providerName.equals(name);
    }

    public boolean matchesProviderId(String id) {
        return this.providerId.equals(id);
    }

    public boolean emailMatches(String otherEmail) {
        return this.providerEmail != null && this.providerEmail.equals(otherEmail);
    }
}
