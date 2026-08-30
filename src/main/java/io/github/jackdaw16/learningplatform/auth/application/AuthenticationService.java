package io.github.jackdaw16.learningplatform.auth.application;

import io.github.jackdaw16.learningplatform.auth.application.exception.InvalidCredentialsException;
import io.github.jackdaw16.learningplatform.auth.application.port.AuthUserRepository;
import io.github.jackdaw16.learningplatform.auth.domain.AuthUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticationResult authenticate(String username, String password) {
        AuthUser authUser = authUserRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, authUser.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthenticationResult(authUser.username(), authUser.role(), authUser.principalId());
    }
}
