package br.com.fiap.techchallenge.oficina.adapters.out.security;

import br.com.fiap.techchallenge.oficina.application.port.out.AppUserRepositoryPort;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class UserDetailsSecurityAdapter implements UserDetailsService {
    private final AppUserRepositoryPort users;

    public UserDetailsSecurityAdapter(AppUserRepositoryPort users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = users.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));
        return User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .roles(user.getRole())
            .build();
    }
}
