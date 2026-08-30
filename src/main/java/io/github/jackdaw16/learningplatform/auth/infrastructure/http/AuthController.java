package io.github.jackdaw16.learningplatform.auth.infrastructure.http;

import io.github.jackdaw16.learningplatform.auth.application.AuthenticationResult;
import io.github.jackdaw16.learningplatform.auth.application.AuthenticationService;
import io.github.jackdaw16.learningplatform.auth.application.IssuedToken;
import io.github.jackdaw16.learningplatform.auth.application.JwtTokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthenticationService authenticationService, JwtTokenService jwtTokenService) {
        this.authenticationService = authenticationService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        AuthenticationResult result = authenticationService.authenticate(request.username(), request.password());
        IssuedToken token = jwtTokenService.issue(result.username(), result.role(), result.principalId());
        return new TokenResponse(token.tokenType(), token.accessToken(), token.expiresInSeconds());
    }
}
