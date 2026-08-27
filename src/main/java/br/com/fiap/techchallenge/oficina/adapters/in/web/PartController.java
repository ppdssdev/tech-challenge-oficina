package br.com.fiap.techchallenge.oficina.adapters.in.web;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.PartRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.PartResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.StockAdjustmentRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.mapper.WebDtoMapper;
import br.com.fiap.techchallenge.oficina.application.port.in.ManagePartsUseCase;
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
@RequestMapping("/api/v1/admin/parts")
public class PartController {

    private final ManagePartsUseCase service;

    public PartController(ManagePartsUseCase service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria peça ou insumo")
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartRequest request) {
        var response = WebDtoMapper.toResponse(service.create(WebDtoMapper.toCommand(request)));
        return ResponseEntity.created(URI.create("/api/v1/admin/parts/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista peças e insumos")
    public List<PartResponse> list(@RequestParam(required = false) Boolean activeOnly) {
        return service.list(Boolean.TRUE.equals(activeOnly)).stream().map(WebDtoMapper::toResponse).toList();
    }

    @GetMapping("/below-minimum-stock")
    @Operation(summary = "Lista peças abaixo ou no estoque mínimo")
    public List<PartResponse> belowMinimumStock() {
        return service.belowMinimumStock().stream().map(WebDtoMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha peça ou insumo")
    public PartResponse detail(@PathVariable UUID id) {
        return WebDtoMapper.toResponse(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza peça ou insumo")
    public PartResponse update(@PathVariable UUID id, @Valid @RequestBody PartRequest request) {
        return WebDtoMapper.toResponse(service.update(id, WebDtoMapper.toCommand(request)));
    }

    @PostMapping("/{id}/stock/increase")
    @Operation(summary = "Adiciona quantidade ao estoque")
    public PartResponse increaseStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return WebDtoMapper.toResponse(service.increaseStock(id, request.quantity()));
    }

    @PostMapping("/{id}/stock/decrease")
    @Operation(summary = "Baixa quantidade do estoque")
    public PartResponse decreaseStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return WebDtoMapper.toResponse(service.decreaseStock(id, request.quantity()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Inativa peça ou insumo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
