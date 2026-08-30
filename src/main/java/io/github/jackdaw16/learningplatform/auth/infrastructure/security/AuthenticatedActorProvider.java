package io.github.jackdaw16.learningplatform.auth.infrastructure.security;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class AuthenticatedActorProvider {

    AuthenticatedActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AccessDeniedException("Authenticated actor is required");
        }

        AuthRole role = jwtAuthentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(this::role)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Authenticated role is required"));

        String actorIdClaim = jwtAuthentication.getToken().getClaimAsString("actorId");
        UUID actorId = actorIdClaim == null ? null : actorId(actorIdClaim);
        if (role != AuthRole.ADMIN && actorId == null) {
            throw new AccessDeniedException("Authenticated actor id is required");
        }
        return new AuthenticatedActor(authentication.getName(), role, actorId);
    }

    private AuthRole role(String value) {
        try {
            return AuthRole.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated role is invalid");
        }
    }

    private UUID actorId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Authenticated actor id is invalid");
        }
    }
}
