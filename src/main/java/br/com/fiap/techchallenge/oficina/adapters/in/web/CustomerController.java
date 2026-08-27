package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer.CustomerRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer.CustomerResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/customers")
public class CustomerController {

    private final ManageCustomersUseCase service;

    public CustomerController(ManageCustomersUseCase service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria cliente")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        var response = WebDtoMapper.toResponse(service.create(WebDtoMapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/customers/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista clientes")
    public List<CustomerResponse> list() {
        return service.list().stream().map(WebDtoMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha cliente")
    public CustomerResponse detail(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza cliente")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return WebDtoMapper.toResponse(service.update(id, WebDtoMapper.toCommand(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove cliente")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
