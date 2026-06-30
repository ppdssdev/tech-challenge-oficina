package br.com.fiap.techchallenge.oficina.application.service;

import br.com.fiap.techchallenge.oficina.api.dto.vehicle.VehicleRequest;
import br.com.fiap.techchallenge.oficina.api.dto.vehicle.VehicleResponse;
import br.com.fiap.techchallenge.oficina.domain.exception.ConflictException;
import br.com.fiap.techchallenge.oficina.domain.exception.NotFoundException;
import br.com.fiap.techchallenge.oficina.domain.model.vehicle.Vehicle;
import br.com.fiap.techchallenge.oficina.domain.service.VehiclePlateValidator;
import br.com.fiap.techchallenge.oficina.infrastructure.repository.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleApplicationService {

    private final VehicleRepository vehicleRepository;
    private final CustomerApplicationService customerService;

    public VehicleApplicationService(VehicleRepository vehicleRepository, CustomerApplicationService customerService) {
        this.vehicleRepository = vehicleRepository;
        this.customerService = customerService;
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String plate = VehiclePlateValidator.normalize(request.plate());
        if (vehicleRepository.existsByPlate(plate)) {
            throw new ConflictException("Já existe veículo cadastrado com essa placa.");
        }
        var customer = customerService.findById(request.customerId());
        var vehicle = new Vehicle(customer, plate, request.brand(), request.model(), request.manufacturingYear());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list() {
        return vehicleRepository.findAll().stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse detail(UUID id) {
        return VehicleResponse.from(findById(id));
    }

    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        var vehicle = findById(id);
        String plate = VehiclePlateValidator.normalize(request.plate());
        vehicleRepository.findByPlate(plate)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new ConflictException("Já existe outro veículo cadastrado com essa placa.");
            });
        var customer = customerService.findById(request.customerId());
        vehicle.update(customer, plate, request.brand(), request.model(), request.manufacturingYear());
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(UUID id) {
        var vehicle = findById(id);
        vehicleRepository.delete(vehicle);
    }

    public Vehicle findById(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new NotFoundException("Veículo não encontrado."));
    }
}
