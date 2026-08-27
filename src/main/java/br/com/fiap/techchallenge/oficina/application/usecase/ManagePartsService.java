package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ManagePartsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PartResult;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class ManagePartsService implements ManagePartsUseCase {
    private final PartRepositoryPort parts;
    private final TransactionPort transactions;

    public ManagePartsService(PartRepositoryPort parts, TransactionPort transactions) {
        this.parts = parts;
        this.transactions = transactions;
    }

    @Override
    public PartResult create(Command command) {
        return transactions.required(() -> {
            String sku = normalizeSku(command.sku());
            if (parts.existsBySku(sku)) {
                throw new ConflictException("Já existe peça/insumo cadastrado com esse SKU.");
            }
            var part = new Part(command.name(), sku, command.unitPrice(), command.quantityInStock(), command.minimumStock());
            part.update(command.name(), sku, command.unitPrice(), command.quantityInStock(), command.minimumStock(), command.active());
            return toResult(parts.save(part));
        });
    }

    @Override
    public List<PartResult> list(boolean activeOnly) {
        return transactions.required(() -> (activeOnly ? parts.findActive() : parts.findAll())
            .stream().map(ApplicationResultMapper::toResult).toList());
    }

    @Override
    public List<PartResult> belowMinimumStock() {
        return transactions.required(() -> parts.findAll().stream()
            .filter(Part::isBelowMinimumStock).map(ApplicationResultMapper::toResult).toList());
    }

    @Override
    public PartResult get(UUID id) {
        return transactions.required(() -> toResult(find(id)));
    }

    @Override
    public PartResult update(UUID id, Command command) {
        return transactions.required(() -> {
            var part = find(id);
            String sku = normalizeSku(command.sku());
            parts.findBySku(sku).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
                throw new ConflictException("Já existe outra peça/insumo com esse SKU.");
            });
            part.update(command.name(), sku, command.unitPrice(), command.quantityInStock(), command.minimumStock(), command.active());
            return toResult(parts.save(part));
        });
    }

    @Override
    public PartResult increaseStock(UUID id, int quantity) {
        return transactions.required(() -> {
            var part = findLocked(id);
            part.increaseStock(quantity);
            return toResult(parts.save(part));
        });
    }

    @Override
    public PartResult decreaseStock(UUID id, int quantity) {
        return transactions.required(() -> {
            var part = findLocked(id);
            part.decreaseStock(quantity);
            return toResult(parts.save(part));
        });
    }

    @Override
    public void deactivate(UUID id) {
        transactions.required(() -> {
            var part = find(id);
            part.deactivate();
            parts.save(part);
            return null;
        });
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }

    private Part find(UUID id) {
        return parts.findById(id).orElseThrow(() -> new NotFoundException("Peça/insumo não encontrado."));
    }

    private Part findLocked(UUID id) {
        return parts.findByIdForStockUpdate(id)
            .orElseThrow(() -> new NotFoundException("Peça/insumo não encontrado."));
    }
}
