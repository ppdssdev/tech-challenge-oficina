package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.CreateWorkOrderUseCase;
import br.com.fiap.techchallenge.oficina.application.port.out.CustomerRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.PartRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.ServiceCatalogRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.VehicleRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.WorkOrderRepositoryPort;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.model.workorder.WorkOrder;
import br.com.fiap.techchallenge.oficina.domain.service.DocumentValidator;
import br.com.fiap.techchallenge.oficina.domain.service.VehiclePlateValidator;
import br.com.fiap.techchallenge.oficina.domain.service.WorkOrderCodeGenerator;

public final class CreateWorkOrderService implements CreateWorkOrderUseCase {

    private final WorkOrderRepositoryPort workOrders;
    private final CustomerRepositoryPort customers;
    private final VehicleRepositoryPort vehicles;
    private final ServiceCatalogRepositoryPort services;
    private final PartRepositoryPort parts;
    private final TransactionPort transactions;

    public CreateWorkOrderService(
        WorkOrderRepositoryPort workOrders,
        CustomerRepositoryPort customers,
        VehicleRepositoryPort vehicles,
        ServiceCatalogRepositoryPort services,
        PartRepositoryPort parts,
        TransactionPort transactions
    ) {
        this.workOrders = workOrders;
        this.customers = customers;
        this.vehicles = vehicles;
        this.services = services;
        this.parts = parts;
        this.transactions = transactions;
    }

    @Override
    public WorkOrder create(Command command) {
        return transactions.required(() -> {
            var customer = resolveCustomer(command.customer());
            var vehicle = resolveVehicle(customer, command.vehicle());
            var order = new WorkOrder(nextUniqueCode(), customer, vehicle, command.diagnosticNotes());

            command.services().forEach(input -> order.addRequestedService(
                services.findById(input.id())
                    .orElseThrow(() -> new NotFoundException("Serviço informado na OS não encontrado.")),
                input.quantity()
            ));
            command.parts().forEach(input -> order.addRequiredPart(
                parts.findById(input.id())
                    .orElseThrow(() -> new NotFoundException("Peça/insumo informado na OS não encontrado.")),
                input.quantity()
            ));
            return workOrders.save(order);
        });
    }

    private Customer resolveCustomer(CustomerData input) {
        String document = DocumentValidator.onlyDigits(input.documentNumber());
        return customers.findByDocumentNumber(document)
            .orElseGet(() -> customers.save(new Customer(
                input.fullName(), input.documentType(), document, input.email(), input.phone()
            )));
    }

    private Vehicle resolveVehicle(Customer customer, VehicleData input) {
        String plate = VehiclePlateValidator.normalize(input.plate());
        return vehicles.findByPlate(plate)
            .map(existing -> {
                if (!existing.getCustomer().getId().equals(customer.getId())) {
                    throw new ConflictException("Veículo já cadastrado para outro cliente.");
                }
                return existing;
            })
            .orElseGet(() -> vehicles.save(new Vehicle(
                customer, plate, input.brand(), input.model(), input.manufacturingYear()
            )));
    }

    private String nextUniqueCode() {
        String code;
        do {
            code = WorkOrderCodeGenerator.generate();
        } while (workOrders.existsByCode(code));
        return code;
    }
}
