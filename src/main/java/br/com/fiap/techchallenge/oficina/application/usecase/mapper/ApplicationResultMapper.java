package br.com.fiap.techchallenge.oficina.application.usecase.mapper;

import br.com.fiap.techchallenge.oficina.application.port.in.result.CustomerResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PartResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.PublicWorkOrderStatusResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.ServiceCatalogResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.VehicleResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderResult;
import br.com.fiap.techchallenge.oficina.application.port.in.result.WorkOrderSummaryResult;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.Part;
import br.com.fiap.techchallenge.oficina.domain.model.catalog.ServiceCatalogItem;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;

public final class ApplicationResultMapper {

    private ApplicationResultMapper() {
    }

    public static CustomerResult toResult(Customer customer) {
        return new CustomerResult(
            customer.getId(), customer.getFullName(), customer.getDocumentType(), customer.getDocumentNumber(),
            customer.getEmail(), customer.getPhone(), customer.getCreatedAt(), customer.getUpdatedAt()
        );
    }

    public static PartResult toResult(Part part) {
        return new PartResult(
            part.getId(), part.getName(), part.getSku(), part.getUnitPrice(), part.getQuantityInStock(),
            part.getMinimumStock(), part.isBelowMinimumStock(), part.isActive(), part.getCreatedAt(), part.getUpdatedAt()
        );
    }

    public static ServiceCatalogResult toResult(ServiceCatalogItem service) {
        return new ServiceCatalogResult(
            service.getId(), service.getName(), service.getDescription(), service.getBasePrice(),
            service.getEstimatedMinutes(), service.isActive(), service.getCreatedAt(), service.getUpdatedAt()
        );
    }

    public static VehicleResult toResult(Vehicle vehicle) {
        return new VehicleResult(
            vehicle.getId(), vehicle.getCustomer().getId(), vehicle.getCustomer().getFullName(),
            vehicle.getCustomer().getDocumentNumber(), vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel(),
            vehicle.getManufacturingYear(), vehicle.getCreatedAt(), vehicle.getUpdatedAt()
        );
    }

    public static WorkOrderResult toResult(WorkOrder order) {
        return new WorkOrderResult(
            order.getId(), order.getCode(), order.getStatus(), order.getStatus().getLabel(),
            order.getCustomer().getId(), order.getCustomer().getFullName(), order.getCustomer().getDocumentNumber(),
            order.getVehicle().getId(), order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel() + " " + order.getVehicle().getManufacturingYear(),
            order.getDiagnosticNotes(), order.getTotalServices(), order.getTotalParts(), order.getTotalAmount(),
            order.getServiceItems().stream().map(item -> new WorkOrderResult.ServiceItem(
                item.getService().getId(), item.getServiceName(), item.getUnitPrice(), item.getQuantity(),
                item.getEstimatedMinutes(), item.getLineTotal()
            )).toList(),
            order.getPartItems().stream().map(item -> new WorkOrderResult.PartItem(
                item.getPart().getId(), item.getPartName(), item.getSku(), item.getUnitPrice(), item.getQuantity(),
                item.getLineTotal(), item.isStockReserved()
            )).toList(),
            order.getCustomerAuthorizedAt(), order.getStartedAt(), order.getFinishedAt(), order.getDeliveredAt(),
            order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    public static WorkOrderSummaryResult toSummaryResult(WorkOrder order) {
        return new WorkOrderSummaryResult(
            order.getId(), order.getCode(), order.getStatus(), order.getStatus().getLabel(),
            order.getCustomer().getFullName(), order.getCustomer().getDocumentNumber(), order.getVehicle().getPlate(),
            order.getVehicle().getBrand() + " " + order.getVehicle().getModel(), order.getTotalAmount(),
            order.getCreatedAt(), order.getUpdatedAt()
        );
    }

    public static PublicWorkOrderStatusResult toPublicStatusResult(WorkOrder order) {
        return new PublicWorkOrderStatusResult(
            order.getCode(), order.getStatus(), order.getStatus().getLabel(), order.getCustomer().getFullName(),
            order.getVehicle().getPlate(), order.getVehicle().getBrand() + " " + order.getVehicle().getModel(),
            order.getDiagnosticNotes(), order.getTotalAmount(), order.getCustomerAuthorizedAt(), order.getStartedAt(),
            order.getFinishedAt(), order.getDeliveredAt(), order.getUpdatedAt()
        );
    }
}
