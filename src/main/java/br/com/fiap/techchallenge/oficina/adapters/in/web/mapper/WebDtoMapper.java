package br.com.fiap.techchallenge.oficina.adapters.in.web.mapper;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.PartRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.PartResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.ServiceCatalogRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.ServiceCatalogResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer.CustomerRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer.CustomerResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle.VehicleRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle.VehicleResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.AverageExecutionTimeResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.CreateWorkOrderRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderPartItemResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderServiceItemResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderSummaryResponse;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase.ExecutionTimeMetrics;
import br.com.fiap.techchallenge.oficina.application.port.in.CreateWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManagePartsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageServiceCatalogUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageVehiclesUseCase;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;

public final class WebDtoMapper {
    private WebDtoMapper() { }

    public static ManageCustomersUseCase.Command toCommand(CustomerRequest request) {
        return new ManageCustomersUseCase.Command(
            request.fullName(), request.documentType(), request.documentNumber(), request.email(), request.phone()
        );
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
            customer.getId(), customer.getFullName(), customer.getDocumentType(), customer.getDocumentNumber(),
            customer.getEmail(), customer.getPhone(), customer.getCreatedAt(), customer.getUpdatedAt()
        );
    }

    public static ManagePartsUseCase.Command toCommand(PartRequest request) {
        return new ManagePartsUseCase.Command(
            request.name(), request.sku(), request.unitPrice(), request.quantityInStock(), request.minimumStock(), request.active()
        );
    }

    public static PartResponse toResponse(Part part) {
        return new PartResponse(
            part.getId(), part.getName(), part.getSku(), part.getUnitPrice(), part.getQuantityInStock(),
            part.getMinimumStock(), part.isBelowMinimumStock(), part.isActive(), part.getCreatedAt(), part.getUpdatedAt()
        );
    }

    public static ManageServiceCatalogUseCase.Command toCommand(ServiceCatalogRequest request) {
        return new ManageServiceCatalogUseCase.Command(
            request.name(), request.description(), request.basePrice(), request.estimatedMinutes(), request.active()
        );
    }

    public static ServiceCatalogResponse toResponse(ServiceCatalogItem item) {
        return new ServiceCatalogResponse(
            item.getId(), item.getName(), item.getDescription(), item.getBasePrice(), item.getEstimatedMinutes(),
            item.isActive(), item.getCreatedAt(), item.getUpdatedAt()
        );
    }

    public static ManageVehiclesUseCase.Command toCommand(VehicleRequest request) {
        return new ManageVehiclesUseCase.Command(
            request.customerId(), request.plate(), request.brand(), request.model(), request.manufacturingYear()
        );
    }

    public static VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(), vehicle.getCustomer().getId(), vehicle.getCustomer().getFullName(),
            vehicle.getCustomer().getDocumentNumber(), vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel(),
            vehicle.getManufacturingYear(), vehicle.getCreatedAt(), vehicle.getUpdatedAt()
        );
    }

    public static CreateWorkOrderUseCase.Command toCommand(CreateWorkOrderRequest request) {
        return new CreateWorkOrderUseCase.Command(
            new CreateWorkOrderUseCase.CustomerData(
                request.customer().fullName(), request.customer().documentType(), request.customer().documentNumber(),
                request.customer().email(), request.customer().phone()
            ),
            new CreateWorkOrderUseCase.VehicleData(
                request.vehicle().plate(), request.vehicle().brand(), request.vehicle().model(),
                request.vehicle().manufacturingYear()
            ),
            request.services().stream().map(item -> new CreateWorkOrderUseCase.Item(item.serviceId(), item.quantity())).toList(),
            request.parts() == null ? java.util.List.of() : request.parts().stream()
                .map(item -> new CreateWorkOrderUseCase.Item(item.partId(), item.quantity())).toList(),
            request.diagnosticNotes()
        );
    }

    public static WorkOrderResponse toResponse(WorkOrder order) {
        return new WorkOrderResponse(
            order.getId(), order.getCode(), order.getStatus(), order.getStatus().getLabel(),
            order.getCustomer().getId(), order.getCustomer().getFullName(), order.getCustomer().getDocumentNumber(),
            order.getVehicle().getId(), order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel() + " " + order.getVehicle().getManufacturingYear(),
            order.getDiagnosticNotes(), order.getTotalServices(), order.getTotalParts(), order.getTotalAmount(),
            order.getServiceItems().stream().map(item -> new WorkOrderServiceItemResponse(
                item.getService().getId(), item.getServiceName(), item.getUnitPrice(), item.getQuantity(),
                item.getEstimatedMinutes(), item.getLineTotal()
            )).toList(),
            order.getPartItems().stream().map(item -> new WorkOrderPartItemResponse(
                item.getPart().getId(), item.getPartName(), item.getSku(), item.getUnitPrice(), item.getQuantity(),
                item.getLineTotal(), item.isStockReserved()
            )).toList(),
            order.getCustomerAuthorizedAt(), order.getStartedAt(), order.getFinishedAt(), order.getDeliveredAt(),
            order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    public static WorkOrderSummaryResponse toSummaryResponse(WorkOrder order) {
        return new WorkOrderSummaryResponse(
            order.getId(), order.getCode(), order.getStatus(), order.getStatus().getLabel(),
            order.getCustomer().getFullName(), order.getCustomer().getDocumentNumber(), order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel(), order.getTotalAmount(),
            order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    public static PublicWorkOrderStatusResponse toPublicResponse(WorkOrder order) {
        return new PublicWorkOrderStatusResponse(
            order.getCode(), order.getStatus(), order.getStatus().getLabel(), order.getCustomer().getFullName(),
            order.getVehicle().getPlate(), order.getVehicle().getBrand() + " " + order.getVehicle().getModel(),
            order.getDiagnosticNotes(), order.getTotalAmount(), order.getCustomerAuthorizedAt(), order.getStartedAt(),
            order.getFinishedAt(), order.getDeliveredAt(), order.getUpdatedAt()
        );
    }

    public static AverageExecutionTimeResponse toResponse(ExecutionTimeMetrics metrics) {
        return new AverageExecutionTimeResponse(
            metrics.completedWorkOrders(), metrics.averageMinutes(), metrics.averageHours()
        );
    }
}
