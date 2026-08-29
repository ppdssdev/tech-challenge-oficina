package br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.AppUserJpaEntity;
import br.com.fiap.techchallenge.oficina.domain.model.user.AppUser;

public final class AppUserPersistenceMapper {
    private AppUserPersistenceMapper() { }

    public static AppUser toDomain(AppUserJpaEntity entity) {
        return AppUser.restore(
            entity.getId(), entity.getUsername(), entity.getPasswordHash(), entity.getRole(),
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public static void updateEntity(AppUser domain, AppUserJpaEntity entity) {
        if (domain.getId() != null) entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setRole(domain.getRole());
    }
}
