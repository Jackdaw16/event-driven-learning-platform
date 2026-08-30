package io.github.jackdaw16.learningplatform.auth.application;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import java.util.UUID;

public record AuthenticationResult(String username, AuthRole role, UUID principalId) {
}
