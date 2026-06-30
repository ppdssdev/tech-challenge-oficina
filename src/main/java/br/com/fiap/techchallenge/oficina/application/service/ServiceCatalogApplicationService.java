package br.com.fiap.techchallenge.oficina.application.service;

import br.com.fiap.techchallenge.oficina.api.dto.catalog.ServiceCatalogRequest;
import br.com.fiap.techchallenge.oficina.api.dto.catalog.ServiceCatalogResponse;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.ServiceCatalogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceCatalogApplicationService {

    private final ServiceCatalogRepository repository;

    public ServiceCatalogApplicationService(ServiceCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceCatalogResponse create(ServiceCatalogRequest request) {
        var item = new ServiceCatalogItem(request.name(), request.description(), request.basePrice(), request.estimatedMinutes());
        item.update(request.name(), request.description(), request.basePrice(), request.estimatedMinutes(), request.active());
        return ServiceCatalogResponse.from(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalogResponse> list(Boolean activeOnly) {
        var items = Boolean.TRUE.equals(activeOnly) ? repository.findByActiveTrueOrderByNameAsc() : repository.findAll();
        return items.stream().map(ServiceCatalogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceCatalogResponse detail(UUID id) {
        return ServiceCatalogResponse.from(findById(id));
    }

    @Transactional
    public ServiceCatalogResponse update(UUID id, ServiceCatalogRequest request) {
        var item = findById(id);
        item.update(request.name(), request.description(), request.basePrice(), request.estimatedMinutes(), request.active());
        return ServiceCatalogResponse.from(item);
    }

    @Transactional
    public void delete(UUID id) {
        var item = findById(id);
        item.deactivate();
    }

    public ServiceCatalogItem findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Serviço não encontrado."));
    }
}
