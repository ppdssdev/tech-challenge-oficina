package br.com.fiap.techchallenge.oficina.domain.model.user;

import br.com.fiap.techchallenge.oficina.domain.model.base.BaseEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AppUser extends BaseEntity {

    private final String username;
    private final String passwordHash;
    private final String role;

    public AppUser(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static AppUser restore(
        UUID id, String username, String passwordHash, String role,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        var user = new AppUser(username, passwordHash, role);
        user.restoreMetadata(id, createdAt, updatedAt);
        return user;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }
}
