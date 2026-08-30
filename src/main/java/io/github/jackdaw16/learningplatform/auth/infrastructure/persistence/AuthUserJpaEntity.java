package io.github.jackdaw16.learningplatform.auth.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "auth_users")
public class AuthUserJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AuthRole role;

    @Column(name = "principal_id")
    private UUID principalId;

    protected AuthUserJpaEntity() {
    }

    UUID id() {
        return id;
    }

    String username() {
        return username;
    }

    String passwordHash() {
        return passwordHash;
    }

    AuthRole role() {
        return role;
    }

    UUID principalId() {
        return principalId;
    }
}
