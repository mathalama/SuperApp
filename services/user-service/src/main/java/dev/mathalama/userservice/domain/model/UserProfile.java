package dev.mathalama.userservice.domain.model;

import dev.mathalama.userservice.domain.enums.ProfileStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String locale = "ru";

    @Column(length = 50)
    private String timezone = "Asia/Tashkent";

    @Column(name = "profile_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ProfileStatus profileStatus = ProfileStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static UserProfile createFromRegistration(UUID userId, String username, String email) {
        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setUsername(username);
        profile.setEmail(email);
        profile.setProfileStatus(ProfileStatus.ACTIVE);
        return profile;
    }
}
