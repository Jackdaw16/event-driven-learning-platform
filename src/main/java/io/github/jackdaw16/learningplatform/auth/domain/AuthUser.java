package io.github.jackdaw16.learningplatform.auth.domain;

import java.util.UUID;

public record AuthUser(
        UUID id,
        String username,
        String passwordHash,
        AuthRole role,
        UUID principalId
) {
}
