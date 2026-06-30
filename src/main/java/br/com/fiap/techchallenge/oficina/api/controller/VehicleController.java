package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.vehicle.VehicleRequest;
import br.com.fiap.techchallenge.oficina.api.dto.vehicle.VehicleResponse;
import br.com.fiap.techchallenge.oficina.application.service.VehicleApplicationService;
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
@RequestMapping("/api/v1/admin/vehicles")
public class VehicleController {

    private final VehicleApplicationService service;

    public VehicleController(VehicleApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria veículo")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/vehicles/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista veículos")
    public List<VehicleResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha veículo")
    public VehicleResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza veículo")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove veículo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
