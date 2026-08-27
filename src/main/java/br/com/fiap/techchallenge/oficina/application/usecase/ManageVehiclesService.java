package br.com.fiap.techchallenge.oficina.application.usecase;

import br.com.fiap.techchallenge.oficina.application.port.in.ManageVehiclesUseCase;
import br.com.fiap.techchallenge.oficina.application.port.in.result.VehicleResult;
import br.com.fiap.techchallenge.oficina.application.port.out.CustomerRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.port.out.TransactionPort;
import br.com.fiap.techchallenge.oficina.application.port.out.VehicleRepositoryPort;
import br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.customer.Customer;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.service.VehiclePlateValidator;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.techchallenge.oficina.application.usecase.mapper.ApplicationResultMapper.toResult;

public final class ManageVehiclesService implements ManageVehiclesUseCase {
    private final VehicleRepositoryPort vehicles;
    private final CustomerRepositoryPort customers;
    private final TransactionPort transactions;

    public ManageVehiclesService(
        VehicleRepositoryPort vehicles,
        CustomerRepositoryPort customers,
        TransactionPort transactions
    ) {
        this.vehicles = vehicles;
        this.customers = customers;
        this.transactions = transactions;
    }

    @Override
    public VehicleResult create(Command command) {
        return transactions.required(() -> {
            String plate = VehiclePlateValidator.normalize(command.plate());
            if (vehicles.existsByPlate(plate)) {
                throw new ConflictException("Já existe veículo cadastrado com essa placa.");
            }
            return toResult(vehicles.save(new Vehicle(
                customer(command.customerId()), plate, command.brand(), command.model(), command.manufacturingYear()
            )));
        });
    }

    @Override
    public List<VehicleResult> list() {
        return transactions.required(() -> vehicles.findAll().stream().map(ApplicationResultMapper::toResult).toList());
    }

    @Override
    public VehicleResult get(UUID id) {
        return transactions.required(() -> toResult(find(id)));
    }

    @Override
    public VehicleResult update(UUID id, Command command) {
        return transactions.required(() -> {
            var vehicle = find(id);
            String plate = VehiclePlateValidator.normalize(command.plate());
            vehicles.findByPlate(plate).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
                throw new ConflictException("Já existe outro veículo cadastrado com essa placa.");
            });
            vehicle.update(customer(command.customerId()), plate, command.brand(), command.model(), command.manufacturingYear());
            return toResult(vehicles.save(vehicle));
        });
    }

    @Override
    public void delete(UUID id) {
        transactions.required(() -> {
            vehicles.delete(find(id));
            return null;
        });
    }

    private Vehicle find(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
    }

    private Customer customer(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
    }
}
