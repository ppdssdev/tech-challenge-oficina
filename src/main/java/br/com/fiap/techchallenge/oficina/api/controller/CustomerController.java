package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.customer.CustomerRequest;
import br.com.fiap.techchallenge.oficina.api.dto.customer.CustomerResponse;
import br.com.fiap.techchallenge.oficina.application.service.CustomerApplicationService;
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

    private final CustomerApplicationService service;

    public CustomerController(CustomerApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria cliente")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/customers/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista clientes")
    public List<CustomerResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha cliente")
    public CustomerResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza cliente")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove cliente")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
