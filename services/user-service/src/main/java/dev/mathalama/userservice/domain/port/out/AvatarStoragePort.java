package dev.mathalama.userservice.domain.port.out;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AvatarStoragePort {
    String uploadAvatar(UUID userId, MultipartFile file);

    void deleteAvatar(String avatarUrl);
}
