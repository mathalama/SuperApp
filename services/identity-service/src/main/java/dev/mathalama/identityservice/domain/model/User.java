package dev.mathalama.identityservice.domain.model;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import dev.mathalama.identityservice.domain.enums.AccountState;
import dev.mathalama.identityservice.domain.enums.KYCLifeCycle;
import dev.mathalama.identityservice.domain.enums.SecurityStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "users_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    private String permissions;

    @Column(name = "account_state", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountState accountState;

    @Column(name = "kyc_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private KYCLifeCycle kycStatus = KYCLifeCycle.PENDING;

    @Column(name = "security_status")
    @Enumerated(EnumType.STRING)
    private SecurityStatus securityStatus;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "verified_at")
    private Date verifiedAt;

    @Column(name = "last_verification_sent_at")
    private Date lastVerificationSentAt;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<OAuthProvider> oauthProviders = new HashSet<>();

    private User(UserBuilder builder) {
        this.email = builder.email;
        this.password = builder.password;
        this.username = builder.username;
        this.roles = builder.roles;
    }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountState != AccountState.DELETED;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountState != AccountState.DISABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountState == AccountState.ACTIVE
                || accountState == AccountState.PENDING_VERIFICATION;
    }

    public static class UserBuilder {
        private String email;
        private String password;
        private String username;
        private Set<Role> roles = new HashSet<>();

        public UserBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder password(String password) {
            this.password = password;
            return this;
        }

        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder roles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public User build() {
            if (email == null || password == null || username == null) {
                throw new IllegalStateException("User fields must not be null");
            }
            return new User(this);
        }
    }
}
