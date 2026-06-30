package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.catalog.ServiceCatalogRequest;
import br.com.fiap.techchallenge.oficina.api.dto.catalog.ServiceCatalogResponse;
import br.com.fiap.techchallenge.oficina.application.service.ServiceCatalogApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/services")
public class ServiceCatalogController {

    private final ServiceCatalogApplicationService service;

    public ServiceCatalogController(ServiceCatalogApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria serviço do catálogo")
    public ResponseEntity<ServiceCatalogResponse> create(@Valid @RequestBody ServiceCatalogRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/services/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista serviços")
    public List<ServiceCatalogResponse> list(@RequestParam(required = false) Boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha serviço")
    public ServiceCatalogResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza serviço")
    public ServiceCatalogResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceCatalogRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Inativa serviço")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
