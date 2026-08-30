package io.github.jackdaw16.learningplatform.auth.application;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.ttl:PT1H}") Duration ttl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public IssuedToken issue(String username, AuthRole role, UUID actorId) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(username)
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role.name());
        if (actorId != null) {
            claims.claim("actorId", actorId.toString());
        }

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        )).getTokenValue();
        return new IssuedToken("Bearer", tokenValue, ttl.toSeconds());
    }
}
