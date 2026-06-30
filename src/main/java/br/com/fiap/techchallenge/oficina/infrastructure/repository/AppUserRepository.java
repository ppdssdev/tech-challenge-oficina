package br.com.fiap.techchallenge.oficina.infrastructure.repository;

import br.com.fiap.techchallenge.oficina.domain.model.user.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
