package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle.VehicleRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle.VehicleResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageVehiclesUseCase;
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

    private final ManageVehiclesUseCase service;

    public VehicleController(ManageVehiclesUseCase service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria veículo")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        var response = WebDtoMapper.toResponse(service.create(WebDtoMapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/vehicles/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista veículos")
    public List<VehicleResponse> list() {
        return service.list().stream().map(WebDtoMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha veículo")
    public VehicleResponse detail(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza veículo")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return WebDtoMapper.toResponse(service.update(id, WebDtoMapper.toCommand(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove veículo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
