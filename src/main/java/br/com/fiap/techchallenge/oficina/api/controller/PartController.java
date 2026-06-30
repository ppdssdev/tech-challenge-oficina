package br.com.fiap.techchallenge.oficina.api.controller;

import br.com.fiap.techchallenge.oficina.api.dto.catalog.PartRequest;
import br.com.fiap.techchallenge.oficina.api.dto.catalog.PartResponse;
import br.com.fiap.techchallenge.oficina.api.dto.catalog.StockAdjustmentRequest;
import br.com.fiap.techchallenge.oficina.application.service.PartApplicationService;
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

    private final PartApplicationService service;

    public PartController(PartApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria peça ou insumo")
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/parts/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista peças e insumos")
    public List<PartResponse> list(@RequestParam(required = false) Boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/below-minimum-stock")
    @Operation(summary = "Lista peças abaixo ou no estoque mínimo")
    public List<PartResponse> belowMinimumStock() {
        return service.belowMinimumStock();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha peça ou insumo")
    public PartResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza peça ou insumo")
    public PartResponse update(@PathVariable UUID id, @Valid @RequestBody PartRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/stock/increase")
    @Operation(summary = "Adiciona quantidade ao estoque")
    public PartResponse increaseStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return service.increaseStock(id, request.quantity());
    }

    @PostMapping("/{id}/stock/decrease")
    @Operation(summary = "Baixa quantidade do estoque")
    public PartResponse decreaseStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return service.decreaseStock(id, request.quantity());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Inativa peça ou insumo")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
