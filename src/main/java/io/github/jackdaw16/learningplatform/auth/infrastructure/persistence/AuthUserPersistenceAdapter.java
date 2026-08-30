package io.github.jackdaw16.learningplatform.auth.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.auth.application.port.AuthUserRepository;
import io.github.jackdaw16.learningplatform.auth.domain.AuthUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthUserPersistenceAdapter implements AuthUserRepository {

    private final SpringDataAuthUserRepository repository;

    public AuthUserPersistenceAdapter(SpringDataAuthUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AuthUser> findByUsername(String username) {
        return repository.findByUsername(username)
                .map(entity -> new AuthUser(
                        entity.id(),
                        entity.username(),
                        entity.passwordHash(),
                        entity.role(),
                        entity.principalId()
                ));
    }
}
