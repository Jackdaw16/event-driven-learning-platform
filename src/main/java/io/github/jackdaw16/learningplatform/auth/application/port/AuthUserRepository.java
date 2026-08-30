package io.github.jackdaw16.learningplatform.auth.application.port;

import io.github.jackdaw16.learningplatform.auth.domain.AuthUser;
import java.util.Optional;

public interface AuthUserRepository {

    Optional<AuthUser> findByUsername(String username);
}
