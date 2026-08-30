package dev.mathalama.identityservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {
    private String sessionId; // jti токена
    private String userId;
    private String ipAddress;
    private String userAgent;
    private String os; // Windows, iOS, Android, macOS, Linux
    private String browser; // Chrome, Safari, Firefox, Edge
    private long createdAt;
    private long lastActiveAt;
}
