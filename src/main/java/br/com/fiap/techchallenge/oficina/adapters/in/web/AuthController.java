package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.auth.LoginRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.auth.LoginResponse;
import br.com.fiap.techchallenge.oficina.application.port.in.AuthenticateUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUserUseCase useCase;

    public AuthController(AuthenticateUserUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica usuário administrativo e retorna JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var token = useCase.authenticate(request.username(), request.password());
        return new LoginResponse(token.tokenType(), token.accessToken(), token.expiresInMinutes());
    }
}
