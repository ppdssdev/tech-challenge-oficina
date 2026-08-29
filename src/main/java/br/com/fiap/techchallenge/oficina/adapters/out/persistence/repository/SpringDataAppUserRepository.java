package br.com.fiap.techchallenge.oficina.adapters.out.persistence.repository;

import br.com.fiap.techchallenge.oficina.adapters.out.persistence.entity.AppUserJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAppUserRepository extends JpaRepository<AppUserJpaEntity, UUID> {
    Optional<AppUserJpaEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
