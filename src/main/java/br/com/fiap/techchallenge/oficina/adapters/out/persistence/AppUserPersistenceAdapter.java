package br.com.fiap.techchallenge.oficina.adapters.out.persistence;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.AppUserJpaEntity;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.mapper.AppUserPersistenceMapper;
import br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository.SpringDataAppUserRepository;
import br.com.fiap.techchallenge.oficina.application.port.out.AppUserRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.user.AppUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AppUserPersistenceAdapter implements AppUserRepositoryPort {
    private final SpringDataAppUserRepository repository;

    public AppUserPersistenceAdapter(SpringDataAppUserRepository repository) { this.repository = repository; }

    @Override
    public AppUser save(AppUser user) {
        var entity = user.getId() == null
            ? new AppUserJpaEntity()
            : repository.findById(user.getId()).orElseGet(AppUserJpaEntity::new);
        AppUserPersistenceMapper.updateEntity(user, entity);
        return AppUserPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override public Optional<AppUser> findByUsername(String username) { return repository.findByUsername(username).map(AppUserPersistenceMapper::toDomain); }
    @Override public boolean existsByUsername(String username) { return repository.existsByUsername(username); }
}
