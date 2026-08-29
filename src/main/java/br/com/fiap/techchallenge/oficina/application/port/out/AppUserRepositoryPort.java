package br.com.fiap.techchallenge.oficina.application.port.out;

import br.com.fiap.techchallenge.oficina.domain.model.user.AppUser;
import java.util.Optional;

public interface AppUserRepositoryPort {
    AppUser save(AppUser user);
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
