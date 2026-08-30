package io.github.jackdaw16.learningplatform.auth.infrastructure.security;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RoleJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String roleClaim = jwt.getClaimAsString("role");
        if (roleClaim == null) {
            throw new IllegalArgumentException("Missing role claim");
        }

        AuthRole role;
        try {
            role = AuthRole.valueOf(roleClaim);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported role claim");
        }

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.name());
        return new JwtAuthenticationToken(jwt, List.of(authority), jwt.getSubject());
    }
}
