package br.com.fiap.techchallenge.oficina.application.service;

import br.com.fiap.techchallenge.oficina.api.dto.workorder.AddWorkOrderItemsRequest;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.AverageExecutionTimeResponse;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.CreateWorkOrderRequest;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.WorkOrderResponse;
import br.com.fiap.techchallenge.oficina.api.dto.workorder.WorkOrderSummaryResponse;
import br.com.fiap.techchallenge.oficina.domain.exception.BusinessException;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrderStatus;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import br.com.fiap.techchallenge.oficina.domain.service.VehiclePlateValidator;
import br.com.fiap.techchallenge.oficina.domain.service.WorkOrderCodeGenerator;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.CustomerRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.PartRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.ServiceCatalogRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.VehicleRepository;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.WorkOrderRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderApplicationService {

    private final WorkOrderRepository workOrderRepository;
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final PartRepository partRepository;

    public WorkOrderApplicationService(
        WorkOrderRepository workOrderRepository,
        CustomerRepository customerRepository,
        VehicleRepository vehicleRepository,
        ServiceCatalogRepository serviceCatalogRepository,
        PartRepository partRepository
    ) {
        this.workOrderRepository = workOrderRepository;
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.partRepository = partRepository;
    }

    @Transactional
    public WorkOrderResponse create(CreateWorkOrderRequest request) {
        var customer = resolveCustomer(request);
        var vehicle = resolveVehicle(customer, request);
        var order = new WorkOrder(nextUniqueCode(), customer, vehicle, request.diagnosticNotes());

        if (request.services() != null) {
            request.services().forEach(input -> {
                var service = serviceCatalogRepository.findById(input.serviceId())
                    .orElseThrow(() -> new NotFoundException("Serviço informado na OS não encontrado."));
                order.addRequestedService(service, input.quantity());
            });
        }

        if (request.parts() != null) {
            request.parts().forEach(input -> {
                var part = partRepository.findById(input.partId())
                    .orElseThrow(() -> new NotFoundException("Peça/insumo informado na OS não encontrado."));
                order.addRequiredPart(part, input.quantity());
            });
        }

        var saved = workOrderRepository.save(order);
        return WorkOrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderSummaryResponse> list(WorkOrderStatus status) {
        var orders = status == null ? workOrderRepository.findAll() : workOrderRepository.findByStatus(status);
        return orders.stream().map(WorkOrderSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse detail(UUID id) {
        return WorkOrderResponse.from(findDetailedById(id));
    }

    @Transactional
    public WorkOrderResponse startDiagnosis(UUID id, String notes) {
        var order = findDetailedById(id);
        order.startDiagnosis(notes);
        return WorkOrderResponse.from(order);
    }

    @Transactional
    public WorkOrderResponse updateDiagnosticNotes(UUID id, String notes) {
        var order = findDetailedById(id);
        order.updateDiagnosticNotes(notes);
        return WorkOrderResponse.from(order);
    }

    @Transactional
    public WorkOrderResponse addItems(UUID id, AddWorkOrderItemsRequest request) {
        var order = findDetailedById(id);
        boolean hasService = request.services() != null && !request.services().isEmpty();
        boolean hasPart = request.parts() != null && !request.parts().isEmpty();
        if (!hasService && !hasPart) {
            throw new BusinessException("Informe ao menos um serviço ou peça para adicionar à OS.");
        }

        if (hasService) {
            request.services().forEach(input -> {
                var service = serviceCatalogRepository.findById(input.serviceId())
                    .orElseThrow(() -> new NotFoundException("Serviço informado na OS não encontrado."));
                order.addRequestedService(service, input.quantity());
            });
        }
        if (hasPart) {
            request.parts().forEach(input -> {
                var part = partRepository.findById(input.partId())
                    .orElseThrow(() -> new NotFoundException("Peça/insumo informado na OS não encontrado."));
                order.addRequiredPart(part, input.quantity());
            });
        }
        return WorkOrderResponse.from(order);
    }

    @Transactional
    public WorkOrderResponse approveBudget(UUID id) {
        var order = findDetailedByIdForStockUpdate(id);
        order.approveBudget();
        return WorkOrderResponse.from(order);
    }

    @Transactional
    public WorkOrderResponse finish(UUID id) {
        var order = findDetailedById(id);
        order.finish();
        return WorkOrderResponse.from(order);
    }

    @Transactional
    public WorkOrderResponse deliver(UUID id) {
        var order = findDetailedById(id);
        order.deliver();
        return WorkOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PublicWorkOrderStatusResponse publicStatus(String code, String documentNumber) {
        var order = workOrderRepository.findByCode(code)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
        String normalizedDocument = DocumentValidator.onlyDigits(documentNumber);
        if (!order.getCustomer().getDocumentNumber().equals(normalizedDocument)) {
            throw new NotFoundException("Ordem de serviço não encontrada para o documento informado.");
        }
        return PublicWorkOrderStatusResponse.from(order);
    }

    @Transactional(readOnly = true)
    public AverageExecutionTimeResponse averageExecutionTime(OffsetDateTime from, OffsetDateTime to) {
        var orders = workOrderRepository.findCompletedForMetrics(from, to);
        var executionMinutes = orders.stream()
            .map(WorkOrder::executionMinutes)
            .filter(minutes -> minutes != null && minutes >= 0)
            .toList();

        if (executionMinutes.isEmpty()) {
            return new AverageExecutionTimeResponse(0, null, null);
        }

        double averageMinutes = executionMinutes.stream().mapToLong(Long::longValue).average().orElse(0);
        return new AverageExecutionTimeResponse(executionMinutes.size(), averageMinutes, averageMinutes / 60.0);
    }

    private Customer resolveCustomer(CreateWorkOrderRequest request) {
        var input = request.customer();
        String document = DocumentValidator.onlyDigits(input.documentNumber());
        return customerRepository.findByDocumentNumberValue(document)
            .orElseGet(() -> customerRepository.save(new Customer(
                input.fullName(), input.documentType(), document, input.email(), input.phone()
            )));
    }

    private Vehicle resolveVehicle(Customer customer, CreateWorkOrderRequest request) {
        var input = request.vehicle();
        String plate = VehiclePlateValidator.normalize(input.plate());
        return vehicleRepository.findByPlateValue(plate)
            .map(existing -> {
                if (!existing.getCustomer().getId().equals(customer.getId())) {
                    throw new ConflictException("Veículo já cadastrado para outro cliente.");
                }
                return existing;
            })
            .orElseGet(() -> vehicleRepository.save(new Vehicle(
                customer, plate, input.brand(), input.model(), input.manufacturingYear()
            )));
    }

    private WorkOrder findDetailedById(UUID id) {
        var order = workOrderRepository.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
        initializeDetailedCollections(id);
        return order;
    }

    private WorkOrder findDetailedByIdForStockUpdate(UUID id) {
        var order = workOrderRepository.findDetailedById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
        workOrderRepository.findDetailedServiceItemsById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
        workOrderRepository.findDetailedPartItemsByIdForStockUpdate(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
        return order;
    }

    private void initializeDetailedCollections(UUID id) {
        // Carrega cada bag em uma query separada para evitar MultipleBagFetchException.
        workOrderRepository.findDetailedServiceItemsById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
        workOrderRepository.findDetailedPartItemsById(id)
            .orElseThrow(() -> new NotFoundException("Ordem de serviço não encontrada."));
    }

    private String
    nextUniqueCode() {
        String code;
        do {
            code = WorkOrderCodeGenerator.generate();
        } while (workOrderRepository.existsByCode(code));
        return code;
    }
}
