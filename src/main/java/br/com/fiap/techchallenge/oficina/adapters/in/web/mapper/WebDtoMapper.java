package br.com.fiap.techchallenge.oficina.adapters.in.web.mapper;

import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.PartRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.PartResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.ServiceCatalogRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.catalog.ServiceCatalogResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer.CustomerRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.customer.CustomerResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle.VehicleRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.vehicle.VehicleResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.AddWorkOrderItemsRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.AverageExecutionTimeResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.BudgetDecisionRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.CreateWorkOrderRequest;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.PublicWorkOrderStatusResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderPartItemResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderServiceItemResponse;
import br.com.fiap.techchallenge.oficina.adapters.in.web.dto.workorder.WorkOrderSummaryResponse;
import br.com.fiap.techchallenge.oficina.application.port.in.AddWorkOrderItemsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.CalculateWorkOrderMetricsUseCase.ExecutionTimeMetrics;
import br.com.fiap.techchallenge.oficina.application.port.in.CreateWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ExternalBudgetDecisionUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageCustomersUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManagePartsUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageServiceCatalogUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.ManageVehiclesUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PartResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.ServiceCatalogResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.VehicleResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderSummaryResult;
import java.util.List;

public final class WebDtoMapper {
    private WebDtoMapper() { }

    public static ManageCustomersUseCase.Command toCommand(CustomerRequest request) {
        return new ManageCustomersUseCase.Command(
            request.fullName(), request.documentType(), request.documentNumber(), request.email(), request.phone()
        );
    }

    public static CustomerResponse toResponse(CustomerResult customer) {
        return new CustomerResponse(
            customer.id(), customer.fullName(), customer.documentType(), customer.documentNumber(),
            customer.email(), customer.phone(), customer.createdAt(), customer.updatedAt()
        );
    }

    public static ManagePartsUseCase.Command toCommand(PartRequest request) {
        return new ManagePartsUseCase.Command(
            request.name(), request.sku(), request.unitPrice(), request.quantityInStock(), request.minimumStock(), request.active()
        );
    }

    public static PartResponse toResponse(PartResult part) {
        return new PartResponse(
            part.id(), part.name(), part.sku(), part.unitPrice(), part.quantityInStock(),
            part.minimumStock(), part.belowMinimumStock(), part.active(), part.createdAt(), part.updatedAt()
        );
    }

    public static ManageServiceCatalogUseCase.Command toCommand(ServiceCatalogRequest request) {
        return new ManageServiceCatalogUseCase.Command(
            request.name(), request.description(), request.basePrice(), request.estimatedMinutes(), request.active()
        );
    }

    public static ServiceCatalogResponse toResponse(ServiceCatalogResult item) {
        return new ServiceCatalogResponse(
            item.id(), item.name(), item.description(), item.basePrice(), item.estimatedMinutes(),
            item.active(), item.createdAt(), item.updatedAt()
        );
    }

    public static ManageVehiclesUseCase.Command toCommand(VehicleRequest request) {
        return new ManageVehiclesUseCase.Command(
            request.customerId(), request.plate(), request.brand(), request.model(), request.manufacturingYear()
        );
    }

    public static VehicleResponse toResponse(VehicleResult vehicle) {
        return new VehicleResponse(
            vehicle.id(), vehicle.customerId(), vehicle.customerName(), vehicle.customerDocumentNumber(),
            vehicle.plate(), vehicle.brand(), vehicle.model(), vehicle.manufacturingYear(),
            vehicle.createdAt(), vehicle.updatedAt()
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

            request.parts() == null ? List.of() : request.parts().stream()
                .map(item -> new CreateWorkOrderUseCase.Item(item.partId(), item.quantity())).toList(),

            request.diagnosticNotes()
        );
    }

    public static AddWorkOrderItemsUseCase.Command toCommand(AddWorkOrderItemsRequest request) {
        var services = request.services() == null ? List.<AddWorkOrderItemsUseCase.ServiceItem>of()
            : request.services().stream()
                .map(item -> new AddWorkOrderItemsUseCase.ServiceItem(item.serviceId(), item.quantity()))
                .toList();
        var parts = request.parts() == null ? List.<AddWorkOrderItemsUseCase.PartItem>of()
            : request.parts().stream()
                .map(item -> new AddWorkOrderItemsUseCase.PartItem(item.partId(), item.quantity()))
                .toList();
        return new AddWorkOrderItemsUseCase.Command(services, parts);
    }

    public static ExternalBudgetDecisionUseCase.Command toCommand(
        String code,
        BudgetDecisionRequest request
    ) {
        return new ExternalBudgetDecisionUseCase.Command(code, request.document());
    }

    public static WorkOrderResponse toResponse(WorkOrderResult order) {
        return new WorkOrderResponse(
            order.id(), order.code(), order.status(), order.statusLabel(),
            order.customerId(), order.customerName(), order.customerDocumentNumber(),
            order.vehicleId(), order.vehiclePlate(), order.vehicleDescription(),
            order.diagnosticNotes(), order.totalServices(), order.totalParts(), order.totalAmount(),
            order.services().stream().map(item -> new WorkOrderServiceItemResponse(
                item.serviceId(), item.name(), item.unitPrice(), item.quantity(),
                item.estimatedMinutes(), item.lineTotal()
            )).toList(),
            order.parts().stream().map(item -> new WorkOrderPartItemResponse(
                item.partId(), item.name(), item.sku(), item.unitPrice(), item.quantity(),
                item.lineTotal(), item.stockReserved()
            )).toList(),
            order.customerAuthorizedAt(), order.startedAt(), order.finishedAt(), order.deliveredAt(),
            order.createdAt(), order.updatedAt()
        );
    }

    public static WorkOrderSummaryResponse toSummaryResponse(WorkOrderSummaryResult order) {
        return new WorkOrderSummaryResponse(
            order.id(), order.code(), order.status(), order.statusLabel(),
            order.customerName(), order.customerDocumentNumber(), order.vehiclePlate(),
            order.vehicleDescription(), order.totalAmount(), order.createdAt(), order.updatedAt()
        );
    }

    public static PublicWorkOrderStatusResponse toPublicResponse(PublicWorkOrderStatusResult order) {
        return new PublicWorkOrderStatusResponse(
            order.code(), order.status(), order.statusLabel(), order.customerName(),
            order.vehiclePlate(), order.vehicleDescription(), order.diagnosticNotes(), order.totalAmount(),
            order.customerAuthorizedAt(), order.startedAt(), order.finishedAt(), order.deliveredAt(), order.updatedAt()
        );
    }

    public static AverageExecutionTimeResponse toResponse(ExecutionTimeMetrics metrics) {
        return new AverageExecutionTimeResponse(
            metrics.completedWorkOrders(), metrics.averageMinutes(), metrics.averageHours()
        );
    }
}
