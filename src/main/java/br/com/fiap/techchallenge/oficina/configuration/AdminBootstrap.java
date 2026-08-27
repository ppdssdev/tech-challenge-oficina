package br.com.fiap.techchallenge.oficina.configuration;

import br.com.fiap.techchallenge.oficina.application.port.out.AppUserRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.model.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {

    @Bean
    CommandLineRunner createDefaultAdmin(
        AppUserRepositoryPort repository,
        PasswordEncoder passwordEncoder,
        @Value("${app.admin.default-username}") String username,
        @Value("${app.admin.default-password}") String password
    ) {
        return args -> {
            if (!repository.existsByUsername(username)) {
                repository.save(new AppUser(username, passwordEncoder.encode(password), "ADMIN"));
            }
        };
    }
}
