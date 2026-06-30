package br.com.fiap.techchallenge.oficina.application.service;

import br.com.fiap.techchallenge.oficina.api.dto.catalog.PartRequest;
import br.com.fiap.techchallenge.oficina.api.dto.catalog.PartResponse;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.PartRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartApplicationService {

    private final PartRepository repository;

    public PartApplicationService(PartRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PartResponse create(PartRequest request) {
        String sku = request.sku().trim().toUpperCase();
        if (repository.existsBySku(sku)) {
            throw new ConflictException("Já existe peça/insumo cadastrado com esse SKU.");
        }
        var part = new Part(request.name(), sku, request.unitPrice(), request.quantityInStock(), request.minimumStock());
        part.update(request.name(), sku, request.unitPrice(), request.quantityInStock(), request.minimumStock(), request.active());
        return PartResponse.from(repository.save(part));
    }

    @Transactional(readOnly = true)
    public List<PartResponse> list(Boolean activeOnly) {
        var parts = Boolean.TRUE.equals(activeOnly) ? repository.findByActiveTrueOrderByNameAsc() : repository.findAll();
        return parts.stream().map(PartResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PartResponse> belowMinimumStock() {
        return repository.findAll().stream()
            .filter(Part::isBelowMinimumStock)
            .map(PartResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public PartResponse detail(UUID id) {
        return PartResponse.from(findById(id));
    }

    @Transactional
    public PartResponse update(UUID id, PartRequest request) {
        var part = findById(id);
        String sku = request.sku().trim().toUpperCase();
        repository.findBySku(sku)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new ConflictException("Já existe outra peça/insumo com esse SKU.");
            });
        part.update(request.name(), sku, request.unitPrice(), request.quantityInStock(), request.minimumStock(), request.active());
        return PartResponse.from(part);
    }

    @Transactional
    public PartResponse increaseStock(UUID id, int quantity) {
        var part = findByIdForStockUpdate(id);
        part.increaseStock(quantity);
        return PartResponse.from(part);
    }

    @Transactional
    public PartResponse decreaseStock(UUID id, int quantity) {
        var part = findByIdForStockUpdate(id);
        part.decreaseStock(quantity);
        return PartResponse.from(part);
    }

    @Transactional
    public void delete(UUID id) {
        var part = findById(id);
        part.deactivate();
    }

    public Part findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Peça/insumo não encontrado."));
    }

    private Part findByIdForStockUpdate(UUID id) {
        return repository.findByIdForStockUpdate(id)
            .orElseThrow(() -> new NotFoundException("Peça/insumo não encontrado."));
    }
}
