package dev.mathalama.identityservice.application.mapper;

import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.application.dto.response.CurrentUserResponse;

import java.util.stream.Collectors;

public final class UserMapper {
    private UserMapper() {}

    public static CurrentUserResponse toCurrentUserResponse(User user) {
        return new CurrentUserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getEmailVerified(),
            user.getAccountState() != null ? user.getAccountState().name() : null,
            user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet()),
            user.getCreatedAt() != null ? user.getCreatedAt().getTime() : null
        );
    }
}
