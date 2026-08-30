package io.github.jackdaw16.learningplatform.auth.infrastructure.security;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import java.util.UUID;

record AuthenticatedActor(String username, AuthRole role, UUID actorId) {
}
