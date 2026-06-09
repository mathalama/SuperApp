package dev.mathalama.identityservice.domain.event;

import java.io.Serializable;
import java.util.UUID;

/**
 * Event published when a new user is registered (via OAuth2 or traditional auth)
 */
public class UserRegisteredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String email;
    private String username;
    private String fullName;
    private String authProvider;
    private long timestamp;

    public UserRegisteredEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public UserRegisteredEvent(UUID userId, String email, String username, String fullName, String authProvider) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.fullName = fullName;
        this.authProvider = authProvider;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "UserRegisteredEvent{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", authProvider='" + authProvider + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
